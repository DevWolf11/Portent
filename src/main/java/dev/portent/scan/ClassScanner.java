package dev.portent.scan;

import dev.portent.Namespaces;
import dev.portent.model.CallSite;
import dev.portent.model.MemberKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * ASM-walks one class and collects everything the detectors need.
 *
 * <p>Parsing only — the class is never defined to a class loader, and never executed.
 */
public final class ClassScanner {

    private ClassScanner() {}

    /**
     * @param archive the nested jar this class came from, or null for the plugin jar itself
     * @param ownerFilter extra rejection on top of the namespace check, used to ignore types the
     *     plugin ships inside its own jar
     */
    public static ClassScan scan(byte[] classBytes, String archive, Predicate<String> ownerFilter) {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<SymbolReference> references = new ArrayList<>();
        Set<String> internals = new LinkedHashSet<>();
        Set<String> apiTypes = new LinkedHashSet<>();
        List<ClassScan.StringConstant> constants = new ArrayList<>();

        // Internals can be reached through the class's own shape, not just its instructions:
        // extending CraftPlayer or declaring an NMS-typed field both count.
        noteInternals(internals, node.superName, ownerFilter);
        if (node.interfaces != null) {
            node.interfaces.forEach(i -> noteInternals(internals, i, ownerFilter));
        }
        for (FieldNode field : node.fields) {
            noteInternalsInDescriptor(internals, field.desc, ownerFilter);
        }

        for (MethodNode method : node.methods) {
            CallSite site = new CallSite(archive, node.name, method.name, method.desc);
            noteInternalsInDescriptor(internals, method.desc, ownerFilter);
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                switch (insn) {
                    case MethodInsnNode call -> {
                        addMember(
                                references, MemberKind.METHOD, call.owner, call.name, call.desc, site,
                                ownerFilter);
                        noteInternals(internals, call.owner, ownerFilter);
                        noteInternalsInDescriptor(internals, call.desc, ownerFilter);
                    }
                    case FieldInsnNode access -> {
                        addMember(
                                references, MemberKind.FIELD, access.owner, access.name, access.desc,
                                site, ownerFilter);
                        noteInternals(internals, access.owner, ownerFilter);
                        noteInternalsInDescriptor(internals, access.desc, ownerFilter);
                    }
                    case TypeInsnNode typeInsn -> noteInternals(internals, typeInsn.desc, ownerFilter);
                    case InvokeDynamicInsnNode indy -> collectHandles(
                            references, internals, indy, site, ownerFilter);
                    case LdcInsnNode ldc -> {
                        if (ldc.cst instanceof String text) {
                            constants.add(new ClassScan.StringConstant(text, site));
                        } else if (ldc.cst instanceof Type type) {
                            noteInternalsInDescriptor(internals, type.getDescriptor(), ownerFilter);
                        }
                    }
                    default -> {}
                }
            }
        }
        for (SymbolReference reference : references) {
            apiTypes.add(reference.owner());
        }
        noteApiType(apiTypes, node.superName, ownerFilter);
        if (node.interfaces != null) {
            node.interfaces.forEach(i -> noteApiType(apiTypes, i, ownerFilter));
        }
        return new ClassScan(node.version & 0xFFFF, references, internals, apiTypes, constants);
    }

    /**
     * A method reference such as {@code Player::getName} compiles to an invokedynamic whose target
     * lives in a bootstrap argument, not in a MethodInsnNode. Real plugins do this to API that has
     * since been removed, so skipping bootstrap handles hides genuine breakage.
     */
    private static void collectHandles(
            List<SymbolReference> references,
            Set<String> internals,
            InvokeDynamicInsnNode indy,
            CallSite site,
            Predicate<String> ownerFilter) {
        for (Object argument : indy.bsmArgs) {
            if (argument instanceof Handle handle) {
                addMember(
                        references,
                        kindOf(handle),
                        handle.getOwner(),
                        handle.getName(),
                        handle.getDesc(),
                        site,
                        ownerFilter);
                noteInternals(internals, handle.getOwner(), ownerFilter);
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

    private static void addMember(
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
                || Namespaces.isServerInternals(owner)
                || !ownerFilter.test(owner)) {
            return;
        }
        references.add(new SymbolReference(kind, owner, name, descriptor, site));
    }

    private static void noteApiType(
            Set<String> apiTypes, String internalName, Predicate<String> ownerFilter) {
        if (internalName == null || internalName.startsWith("[")) {
            return;
        }
        if (Namespaces.isReportable(internalName)
                && !Namespaces.isServerInternals(internalName)
                && ownerFilter.test(internalName)) {
            apiTypes.add(internalName);
        }
    }

    private static void noteInternals(
            Set<String> internals, String internalName, Predicate<String> ownerFilter) {
        if (internalName == null) {
            return;
        }
        String name = internalName.startsWith("[") ? elementOf(internalName) : internalName;
        if (name != null && Namespaces.isServerInternals(name) && ownerFilter.test(name)) {
            internals.add(name);
        }
    }

    private static void noteInternalsInDescriptor(
            Set<String> internals, String descriptor, Predicate<String> ownerFilter) {
        if (descriptor == null || descriptor.indexOf('L') < 0) {
            return;
        }
        int i = 0;
        while ((i = descriptor.indexOf('L', i)) >= 0) {
            int end = descriptor.indexOf(';', i);
            if (end < 0) {
                return;
            }
            noteInternals(internals, descriptor.substring(i + 1, end), ownerFilter);
            i = end + 1;
        }
    }

    private static String elementOf(String arrayDescriptor) {
        int i = 0;
        while (i < arrayDescriptor.length() && arrayDescriptor.charAt(i) == '[') {
            i++;
        }
        if (i < arrayDescriptor.length() && arrayDescriptor.charAt(i) == 'L') {
            int end = arrayDescriptor.indexOf(';', i);
            return end < 0 ? null : arrayDescriptor.substring(i + 1, end);
        }
        return null;
    }
}
