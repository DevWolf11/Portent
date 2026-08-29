package dev.plugindoctor.scan;

import dev.plugindoctor.model.UnreadableClass;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Collects every class in a plugin jar, including those inside nested jars.
 *
 * <p>Modern plugins routinely ship almost all of their code inside a bundled jar — LuckPerms puts
 * 709 of its 905 classes in {@code luckperms-bukkit.jarinjar}, leaving 196 loader classes visible
 * at the top level. A scanner that only reads the outer jar sees 1 Bukkit reference instead of 356
 * and calls the plugin GREEN, which is worse than saying nothing at all.
 *
 * <p>Nested jars are read as byte streams and parsed by ASM. Nothing is extracted to disk, and
 * nothing is loaded or executed. Because the input is untrusted, both nesting depth and total
 * uncompressed size are capped.
 */
public final class ArchiveWalker {

    /** Deep enough for jar-in-jar-in-jar; anything past this is not a real packaging scheme. */
    static final int MAX_DEPTH = 3;

    /** Cap on buffered class bytes, so a decompression bomb cannot exhaust the heap. */
    static final long MAX_CLASS_BYTES = 128L * 1024 * 1024;

    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";

    private ArchiveWalker() {}

    /**
     * @param internalName the class's real internal name, with any multi-release prefix stripped
     * @param archive the nested jar it came from, or null when it sits in the plugin jar itself
     */
    public record ClassEntry(String internalName, String archive, byte[] bytes) {}

    /**
     * @param truncated true if a size or depth cap stopped the walk, so findings may be incomplete
     */
    public record Contents(
            List<ClassEntry> classes, List<UnreadableClass> unreadable, boolean truncated) {}

    public static Contents collect(ZipFile jar) {
        List<ClassEntry> classes = new ArrayList<>();
        List<UnreadableClass> unreadable = new ArrayList<>();
        long[] budget = {MAX_CLASS_BYTES};

        Enumeration<? extends ZipEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            try (InputStream in = jar.getInputStream(entry)) {
                if (isClass(name)) {
                    addClass(classes, null, name, readBounded(in, budget), budget);
                } else if (isNestedArchive(name)) {
                    collectNested(in, name, 1, classes, unreadable, budget);
                }
            } catch (IOException | RuntimeException e) {
                unreadable.add(new UnreadableClass(name, describe(e)));
            }
        }
        return new Contents(classes, unreadable, budget[0] <= 0);
    }

    private static void collectNested(
            InputStream archiveStream,
            String archiveName,
            int depth,
            List<ClassEntry> classes,
            List<UnreadableClass> unreadable,
            long[] budget) {
        if (depth > MAX_DEPTH || budget[0] <= 0) {
            return;
        }
        try (ZipInputStream zip = new ZipInputStream(archiveStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String qualified = archiveName + "!" + name;
                try {
                    if (isClass(name)) {
                        addClass(classes, archiveName, name, readBounded(zip, budget), budget);
                    } else if (isNestedArchive(name)) {
                        collectNested(zip, qualified, depth + 1, classes, unreadable, budget);
                    }
                } catch (IOException | RuntimeException e) {
                    unreadable.add(new UnreadableClass(qualified, describe(e)));
                }
                if (budget[0] <= 0) {
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            unreadable.add(new UnreadableClass(archiveName, describe(e)));
        }
    }

    private static void addClass(
            List<ClassEntry> classes, String archive, String entryName, byte[] bytes, long[] budget) {
        if (bytes.length == 0 || budget[0] <= 0) {
            return;
        }
        classes.add(new ClassEntry(internalNameOf(entryName), archive, bytes));
    }

    /**
     * Strips the {@code META-INF/versions/<n>/} prefix from multi-release entries so they carry the
     * class's real name. ViaVersion ships two such classes; left unstripped they would be recorded
     * under a name no reference could ever match.
     */
    static String internalNameOf(String entryName) {
        String name = entryName.substring(0, entryName.length() - ".class".length());
        if (!name.startsWith(MULTI_RELEASE_PREFIX)) {
            return name;
        }
        int slash = name.indexOf('/', MULTI_RELEASE_PREFIX.length());
        return slash < 0 ? name : name.substring(slash + 1);
    }

    static boolean isClass(String entryName) {
        return entryName.endsWith(".class")
                && !entryName.endsWith("module-info.class")
                && !entryName.endsWith("package-info.class");
    }

    /** {@code .jarinjar} is LuckPerms' extension; Paper and Fabric loaders use plain {@code .jar}. */
    static boolean isNestedArchive(String entryName) {
        return entryName.endsWith(".jar") || entryName.endsWith(".jarinjar");
    }

    /** Reads at most the remaining budget, so a lying entry size cannot blow the heap. */
    private static byte[] readBounded(InputStream in, long[] budget) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) > 0) {
            if (budget[0] - read <= 0) {
                budget[0] = 0;
                return out.toByteArray();
            }
            budget[0] -= read;
            out.write(chunk, 0, read);
        }
        return out.toByteArray();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }
}
