package dev.portent.scan;

import dev.portent.index.ApiIndex;
import dev.portent.index.TypeInfo;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Answers "does this member exist on the target server?" by walking the owner's superclass and
 * interface hierarchy.
 *
 * <p>Inherited members are the single largest source of false positives in a tool like this — a
 * plugin calling {@code player.getName()} references it on {@code Player} even though it is
 * declared several types up — so the walk is mandatory rather than an optimisation.
 *
 * <p>Whenever the walk meets a type it cannot see, it answers {@link Resolution#UNKNOWN} rather
 * than guessing that the member is gone.
 */
public final class MemberResolver {

    private static final String OBJECT = "java/lang/Object";

    private final ApiIndex index;
    private final TypeSource fallback;

    public MemberResolver(ApiIndex index) {
        this(index, new PlatformTypeSource());
    }

    public MemberResolver(ApiIndex index, TypeSource fallback) {
        this.index = index;
        this.fallback = fallback;
    }

    /**
     * Resolve a method reference.
     *
     * <p>Constructors are not inherited, so {@code <init>} is only ever looked for on the owner
     * itself.
     */
    public MemberLookup resolveMethod(String owner, String name, String descriptor) {
        String key = TypeInfo.methodKey(name, descriptor);
        if ("<init>".equals(name) || "<clinit>".equals(name)) {
            TypeInfo info = lookup(owner);
            if (info == null) {
                return MemberLookup.UNKNOWN;
            }
            return info.hasMethod(key)
                    ? MemberLookup.present(owner, combinedFlags(info, key))
                    : MemberLookup.ABSENT;
        }
        return walk(owner, key, TypeInfo::hasMethod);
    }

    /** Resolve a field reference. */
    public MemberLookup resolveField(String owner, String name, String descriptor) {
        String key = TypeInfo.fieldKey(name, descriptor);
        return walk(owner, key, TypeInfo::hasField);
    }

    /**
     * Breadth-first over superclasses and interfaces. Java's own resolution order matters for
     * <em>which</em> member wins, but we only ask whether one exists at all, so any order is
     * correct as long as the whole hierarchy is visited.
     */
    private MemberLookup walk(String owner, String key, BiPredicate<TypeInfo, String> declares) {
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(owner);
        boolean sawUnknownType = false;

        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            TypeInfo info = lookup(current);
            if (info == null) {
                sawUnknownType = true;
                continue;
            }
            if (declares.test(info, key)) {
                return MemberLookup.present(current, combinedFlags(info, key));
            }
            String superName = superOf(current, info);
            if (superName != null) {
                pending.add(superName);
            }
            pending.addAll(info.interfaces());
        }
        return sawUnknownType ? MemberLookup.UNKNOWN : MemberLookup.ABSENT;
    }

    /**
     * A member of an internal or deprecated <em>type</em> is itself internal or deprecated, so the
     * type's own markers are folded in alongside the member's.
     */
    private static String combinedFlags(TypeInfo declaring, String memberKey) {
        String member = declaring.flagsOf(memberKey);
        String type = declaring.flags();
        if (member == null) {
            return type;
        }
        if (type == null) {
            return member;
        }
        StringBuilder combined = new StringBuilder(member);
        for (int i = 0; i < type.length(); i++) {
            if (combined.indexOf(String.valueOf(type.charAt(i))) < 0) {
                combined.append(type.charAt(i));
            }
        }
        return combined.toString();
    }

    /** A null {@code superName} means {@code java/lang/Object}, except on Object itself. */
    private static String superOf(String internalName, TypeInfo info) {
        if (info.superName() != null) {
            return info.superName();
        }
        return OBJECT.equals(internalName) ? null : OBJECT;
    }

    private TypeInfo lookup(String internalName) {
        TypeInfo info = index.type(internalName);
        return info != null ? info : fallback.find(internalName);
    }
}
