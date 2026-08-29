package dev.portent.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Works out which supertypes a set of jars refers to but does not contain.
 *
 * <p>This is what decides whether another jar is worth downloading. paper-api needs Adventure
 * because its types inherit from it; it does not need Guava's transitive closure, or JUnit, merely
 * because they appear somewhere in a POM.
 */
public final class Supertypes {

    private Supertypes() {}

    /** Internal names referenced as a superclass or interface but defined in none of the jars. */
    public static Set<String> unresolvedIn(List<Path> jars) throws IOException {
        Set<String> defined = new HashSet<>();
        Set<String> referenced = new HashSet<>();

        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        collect(in, defined, referenced);
                    } catch (RuntimeException e) {
                        // A class we cannot parse simply contributes nothing.
                    }
                }
            }
        }

        Set<String> missing = new TreeSet<>(referenced);
        missing.removeAll(defined);
        // The JDK is resolvable from the running JVM at scan time, so it is never a gap.
        missing.removeIf(
                name ->
                        name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/"));
        return missing;
    }

    /** True if this jar defines any of the wanted types. The only reason to keep a download. */
    public static boolean definesAnyOf(Path jar, Set<String> wanted) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String type : wanted) {
                if (zip.getEntry(type + ".class") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collect(InputStream in, Set<String> defined, Set<String> referenced)
            throws IOException {
        new ClassReader(in)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(
                                    int version,
                                    int access,
                                    String name,
                                    String signature,
                                    String superName,
                                    String[] interfaces) {
                                defined.add(name);
                                if (superName != null) {
                                    referenced.add(superName);
                                }
                                if (interfaces != null) {
                                    for (String each : interfaces) {
                                        referenced.add(each);
                                    }
                                }
                            }
                        },
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
