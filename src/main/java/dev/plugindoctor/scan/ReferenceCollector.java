package dev.plugindoctor.scan;

import dev.plugindoctor.Namespaces;
import dev.plugindoctor.model.CallSite;
import dev.plugindoctor.model.MemberKind;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * ASM-walks one class and collects the Bukkit/Paper members it references.
 *
 * <p>Parsing only — the class is never defined to a class loader, and never executed.
 */
public final class ReferenceCollector {

    private ReferenceCollector() {}

    public static List<SymbolReference> collect(InputStream classBytes, Predicate<String> ownerFilter)
            throws IOException {
        return collect(classBytes.readAllBytes(), null, ownerFilter);
    }

    /**
     * @param archive the nested jar this class came from, or null for the plugin jar itself
     * @param ownerFilter extra rejection on top of the namespace check, used to ignore Bukkit-named
     *     classes the plugin ships inside its own jar
     */
    public static List<SymbolReference> collect(
            byte[] classBytes, String archive, Predicate<String> ownerFilter) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<SymbolReference> references = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            CallSite site = new CallSite(archive, node.name, method.name, method.desc);
            for (AbstractInsnNode insn : method.instructions) {
                switch (insn) {
                    case MethodInsnNode call -> add(
                            references, MemberKind.METHOD, call.owner, call.name, call.desc, site, ownerFilter);
                    case FieldInsnNode access -> add(
                            references,
                            MemberKind.FIELD,
                            access.owner,
                            access.name,
                            access.desc,
                            site,
                            ownerFilter);
                    case InvokeDynamicInsnNode indy -> collectHandles(
                            references, indy, site, ownerFilter);
                    default -> {}
                }
            }
        }
        return references;
    }

    /**
     * A method reference such as {@code Player::getName} compiles to an invokedynamic whose target
     * lives in a bootstrap argument, not in a MethodInsnNode. Real plugins do this to API that has
     * since been removed — ViaVersion reaches {@code PlayerInventory.setItemInHand} exactly this
     * way — so skipping bootstrap handles hides genuine breakage.
     */
    private static void collectHandles(
            List<SymbolReference> references,
            InvokeDynamicInsnNode indy,
            CallSite site,
            Predicate<String> ownerFilter) {
        for (Object argument : indy.bsmArgs) {
            if (argument instanceof Handle handle) {
                add(
                        references,
                        kindOf(handle),
                        handle.getOwner(),
                        handle.getName(),
                        handle.getDesc(),
                        site,
                        ownerFilter);
            }
        }
    }

    private static MemberKind kindOf(Handle handle) {
        return switch (handle.getTag()) {
            case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC ->
                    MemberKind.FIELD;
            default -> MemberKind.METHOD;
        };
    }

    private static void add(
            List<SymbolReference> references,
            MemberKind kind,
            String owner,
            String name,
            String descriptor,
            CallSite site,
            Predicate<String> ownerFilter) {
        // Array owners (`[Lorg/bukkit/Material;`) carry only java.lang.Object's methods.
        if (owner == null
                || owner.startsWith("[")
                || !Namespaces.isReportable(owner)
                || !ownerFilter.test(owner)) {
            return;
        }
        references.add(new SymbolReference(kind, owner, name, descriptor, site));
    }
}
