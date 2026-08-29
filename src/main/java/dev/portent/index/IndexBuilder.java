package dev.portent.index;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Builds an {@link ApiIndex} by ASM-walking every class in a local API jar. Nothing is loaded or
 * executed, and nothing is fetched over the network.
 */
public final class IndexBuilder {

    private static final String DEPRECATED_DESC = "Ljava/lang/Deprecated;";
    private static final String API_STATUS_INTERNAL = "Lorg/jetbrains/annotations/ApiStatus$Internal;";
    private static final String API_STATUS_EXPERIMENTAL =
            "Lorg/jetbrains/annotations/ApiStatus$Experimental;";

    private IndexBuilder() {}

    public static ApiIndex fromJar(Path apiJar) throws IOException {
        return fromJar(apiJar, null, 0);
    }

    public static ApiIndex fromJar(Path apiJar, String minecraftVersion, int javaVersion)
            throws IOException {
        return fromJars(List.of(apiJar), minecraftVersion, javaVersion);
    }

    /**
     * Builds an index from the API jar plus any jars needed to complete its type hierarchies.
     *
     * <p>paper-api declares Adventure as a Maven dependency rather than bundling it, so on its own
     * 16.5% of its types -- everything implementing {@code Audience}, {@code Translatable} or
     * {@code Keyed} -- has a supertype the index cannot see. The walk then answers UNKNOWN for
     * members it should have called missing. Passing the supporting jars closes that gap.
     *
     * <p>Only the first jar is treated as the API proper: its Maven metadata names the target
     * version and its class files fix the Java release. The rest contribute types only. Which
     * symbols are worth reporting is still decided by namespace, so adding a supporting jar never
     * introduces findings about the library itself.
     *
     * @param minecraftVersion overrides the version read from the jar's Maven metadata; may be null
     * @param javaVersion overrides the release inferred from class file versions; 0 to infer
     */
    public static ApiIndex fromJars(List<Path> jars, String minecraftVersion, int javaVersion)
            throws IOException {
        if (jars == null || jars.isEmpty()) {
            throw new IOException("no API jar given");
        }
        Map<String, TypeInfo> types = new TreeMap<>();
        int primaryMajor = 0;
        String mavenVersion = null;

        for (int i = 0; i < jars.size(); i++) {
            Path jar = jars.get(i);
            boolean primary = i == 0;
            JarCheck.require(jar);

            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (primary && isPomProperties(entry)) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            mavenVersion = readMavenVersion(in);
                        }
                        continue;
                    }
                    if (!isClassEntry(entry)) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        ClassNode node = read(in);
                        // The API jar wins if a supporting jar happens to ship the same type.
                        types.merge(node.name, describe(node), (existing, added) -> existing);
                        if (primary) {
                            primaryMajor = Math.max(primaryMajor, node.version & 0xFFFF);
                        }
                    }
                }
            }
        }

        String resolvedVersion = minecraftVersion != null ? minecraftVersion : mavenVersion;
        int resolvedJava = javaVersion > 0 ? javaVersion : releaseOf(primaryMajor);
        return new ApiIndex(
                ApiIndex.CURRENT_FORMAT_VERSION,
                jars.getFirst().getFileName().toString(),
                resolvedVersion,
                resolvedJava,
                types);
    }

    /** Class file major 65 is Java 21, 69 is Java 25. */
    static int releaseOf(int classFileMajor) {
        return classFileMajor >= 45 ? classFileMajor - 44 : 0;
    }

    private static boolean isPomProperties(ZipEntry entry) {
        return !entry.isDirectory()
                && entry.getName().startsWith("META-INF/maven/")
                && entry.getName().endsWith("/pom.properties");
    }

    /** paper-api ships its own coordinates; the version there names the server release. */
    private static String readMavenVersion(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        String version = properties.getProperty("version");
        return version == null || version.isBlank() ? null : version;
    }

    static boolean isClassEntry(ZipEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        String name = entry.getName();
        return name.endsWith(".class")
                && !name.endsWith("module-info.class")
                && !name.endsWith("package-info.class");
    }

    private static ClassNode read(InputStream in) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(in)
                .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static TypeInfo describe(ClassNode node) {
        Set<String> methods = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        Map<String, String> memberFlags = new TreeMap<>();

        for (MethodNode method : sortedMethods(node)) {
            if (!isApiVisible(method.access)) {
                continue;
            }
            String key = TypeInfo.methodKey(method.name, method.desc);
            methods.add(key);
            record(memberFlags, key, method.access, method.visibleAnnotations, method.invisibleAnnotations);
        }
        for (FieldNode field : sortedFields(node)) {
            if (!isApiVisible(field.access)) {
                continue;
            }
            String key = TypeInfo.fieldKey(field.name, field.desc);
            fields.add(key);
            record(memberFlags, key, field.access, field.visibleAnnotations, field.invisibleAnnotations);
        }

        String superName = "java/lang/Object".equals(node.superName) ? null : node.superName;
        return new TypeInfo(
                superName,
                node.interfaces == null ? List.of() : new ArrayList<>(node.interfaces),
                flagsOf(node.access, node.visibleAnnotations, node.invisibleAnnotations),
                methods,
                fields,
                memberFlags);
    }

    private static void record(
            Map<String, String> memberFlags,
            String key,
            int access,
            List<AnnotationNode> visible,
            List<AnnotationNode> invisible) {
        String flags = flagsOf(access, visible, invisible);
        if (flags != null) {
            memberFlags.put(key, flags);
        }
    }

    /** Sorted so that an index built twice from the same jar is byte-identical. */
    private static List<MethodNode> sortedMethods(ClassNode node) {
        List<MethodNode> methods = new ArrayList<>(node.methods);
        methods.sort(Comparator.comparing((MethodNode m) -> m.name).thenComparing(m -> m.desc));
        return methods;
    }

    private static List<FieldNode> sortedFields(ClassNode node) {
        List<FieldNode> fields = new ArrayList<>(node.fields);
        fields.sort(Comparator.comparing((FieldNode f) -> f.name).thenComparing(f -> f.desc));
        return fields;
    }

    /** Only members a plugin could legally bind against. */
    private static boolean isApiVisible(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0
                && (access & Opcodes.ACC_SYNTHETIC) == 0;
    }

    private static String flagsOf(
            int access, List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        StringBuilder flags = new StringBuilder(2);
        boolean deprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
        boolean forRemoval = false;
        boolean internal = false;
        boolean experimental = false;

        for (AnnotationNode annotation : concat(visible, invisible)) {
            switch (annotation.desc) {
                case DEPRECATED_DESC -> {
                    deprecated = true;
                    forRemoval |= readForRemoval(annotation);
                }
                case API_STATUS_INTERNAL -> internal = true;
                case API_STATUS_EXPERIMENTAL -> experimental = true;
                default -> {}
            }
        }
        if (deprecated) {
            flags.append(ApiFlags.DEPRECATED);
        }
        if (forRemoval) {
            flags.append(ApiFlags.FOR_REMOVAL);
        }
        if (internal) {
            flags.append(ApiFlags.INTERNAL);
        }
        if (experimental) {
            flags.append(ApiFlags.EXPERIMENTAL);
        }
        return flags.isEmpty() ? null : flags.toString();
    }

    private static boolean readForRemoval(AnnotationNode annotation) {
        List<Object> values = annotation.values;
        if (values == null) {
            return false;
        }
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if ("forRemoval".equals(values.get(i)) && Boolean.TRUE.equals(values.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<AnnotationNode> concat(List<AnnotationNode> a, List<AnnotationNode> b) {
        if (a == null && b == null) {
            return List.of();
        }
        List<AnnotationNode> all = new ArrayList<>();
        if (a != null) {
            all.addAll(a);
        }
        if (b != null) {
            all.addAll(b);
        }
        return all;
    }
}
