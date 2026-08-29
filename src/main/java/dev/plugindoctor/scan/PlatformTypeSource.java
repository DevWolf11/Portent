package dev.plugindoctor.scan;

import dev.plugindoctor.Namespaces;
import dev.plugindoctor.index.TypeInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Describes JDK types by reading their class files out of the running JVM's module image.
 *
 * <p>The hierarchy walk needs these: {@code Material.name()} is declared on {@code java.lang.Enum}
 * and {@code Inventory.forEach(..)} on {@code java.lang.Iterable}, so without them every such call
 * would look like a removed Bukkit member.
 *
 * <p>This reads class files as resources — it never loads or initialises a class — and it is
 * restricted to {@code java/}, {@code javax/} and {@code jdk/} so it can never accidentally answer
 * for a type from a scanned jar or from plugin-doctor's own dependencies.
 */
public final class PlatformTypeSource implements TypeSource {

    private final Map<String, Optional<TypeInfo>> cache = new HashMap<>();

    @Override
    public TypeInfo find(String internalName) {
        if (!Namespaces.isPlatform(internalName)) {
            return null;
        }
        return cache.computeIfAbsent(internalName, PlatformTypeSource::load).orElse(null);
    }

    private static Optional<TypeInfo> load(String internalName) {
        ClassLoader loader = ClassLoader.getPlatformClassLoader();
        try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return Optional.empty();
            }
            ClassNode node = new ClassNode();
            new ClassReader(in)
                    .accept(
                            node,
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return Optional.of(describe(node));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static TypeInfo describe(ClassNode node) {
        Set<String> methods = new LinkedHashSet<>();
        for (MethodNode method : node.methods) {
            if (isApiVisible(method.access)) {
                methods.add(TypeInfo.methodKey(method.name, method.desc));
            }
        }
        Set<String> fields = new LinkedHashSet<>();
        for (FieldNode field : node.fields) {
            if (isApiVisible(field.access)) {
                fields.add(TypeInfo.fieldKey(field.name, field.desc));
            }
        }
        String superName = "java/lang/Object".equals(node.superName) ? null : node.superName;
        List<String> interfaces =
                node.interfaces == null ? List.of() : new ArrayList<>(node.interfaces);
        return new TypeInfo(superName, interfaces, null, methods, fields, null);
    }

    private static boolean isApiVisible(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }
}
