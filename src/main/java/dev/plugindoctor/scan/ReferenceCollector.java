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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * ASM-walks one class and collects the Bukkit/Paper members it references.
 *
 * <p>Parsing only — the class is never defined to a class loader, and never executed.
 */
public final class ReferenceCollector {

    private ReferenceCollector() {}

    /**
     * @param ownerFilter extra rejection on top of the namespace check, used to ignore Bukkit-named
     *     classes the plugin ships inside its own jar
     */
    public static List<SymbolReference> collect(InputStream classBytes, Predicate<String> ownerFilter)
            throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        List<SymbolReference> references = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            CallSite site = new CallSite(node.name, method.name, method.desc);
            for (AbstractInsnNode insn : method.instructions) {
                switch (insn) {
                    case MethodInsnNode call -> {
                        if (accepts(call.owner, ownerFilter)) {
                            references.add(
                                    new SymbolReference(
                                            MemberKind.METHOD, call.owner, call.name, call.desc, site));
                        }
                    }
                    case FieldInsnNode access -> {
                        if (accepts(access.owner, ownerFilter)) {
                            references.add(
                                    new SymbolReference(
                                            MemberKind.FIELD,
                                            access.owner,
                                            access.name,
                                            access.desc,
                                            site));
                        }
                    }
                    default -> {}
                }
            }
        }
        return references;
    }

    private static boolean accepts(String owner, Predicate<String> ownerFilter) {
        // Array owners (`[Lorg/bukkit/Material;`) carry only java.lang.Object's methods.
        return owner != null
                && !owner.startsWith("[")
                && Namespaces.isReportable(owner)
                && ownerFilter.test(owner);
    }
}
