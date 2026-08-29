package dev.portent.scan;

import dev.portent.index.ApiIndex;
import dev.portent.model.CallSite;
import dev.portent.model.PluginReport;
import dev.portent.model.UnreadableClass;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Scans a single jar against a target API index. */
public final class JarScanner {

    private final PluginAnalyser analyser;

    public JarScanner(ApiIndex index) {
        this.analyser = new PluginAnalyser(index);
    }

    public PluginReport scan(Path jar) {
        String fileName = jar.getFileName().toString();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry descriptorEntry = findDescriptor(zip);
            if (descriptorEntry == null) {
                return PluginReport.skipped(
                        fileName,
                        "no " + DescriptorParser.BUKKIT_DESCRIPTOR + " or "
                                + DescriptorParser.PAPER_DESCRIPTOR);
            }
            PluginDescriptor descriptor = readDescriptor(zip, descriptorEntry);
            if (descriptor == null) {
                return PluginReport.skipped(fileName, "unreadable " + descriptorEntry.getName());
            }
            return scanClasses(fileName, zip, descriptor);
        } catch (IOException e) {
            return PluginReport.skipped(fileName, "not a readable jar: " + message(e));
        }
    }

    private PluginReport scanClasses(String fileName, ZipFile zip, PluginDescriptor descriptor) {
        ArchiveWalker.Contents contents = ArchiveWalker.collect(zip);

        // Types the plugin ships itself are present at runtime, so a reference to one says nothing
        // about the server version.
        Set<String> ownTypes = new HashSet<>();
        for (ArchiveWalker.ClassEntry entry : contents.classes()) {
            ownTypes.add(entry.internalName());
        }

        PluginAnalyser.Collector collector = new PluginAnalyser.Collector();
        List<UnreadableClass> unreadable = new ArrayList<>(contents.unreadable());
        int classesScanned = 0;

        for (ArchiveWalker.ClassEntry entry : contents.classes()) {
            try {
                ClassScan scan =
                        ClassScanner.scan(
                                entry.bytes(), entry.archive(), owner -> !ownTypes.contains(owner));
                // A multi-release copy is only ever loaded by a JVM that already supports it, so its
                // class file version is not evidence that the plugin needs a newer Java.
                analyser.accept(collector, scan, classSite(entry), !entry.multiRelease());
                classesScanned++;
            } catch (RuntimeException e) {
                // A malformed class is the plugin's problem, not a reason to abandon the jar.
                unreadable.add(new UnreadableClass(label(entry), message(e)));
            }
        }
        return PluginReport.scanned(
                fileName, descriptor, analyser.findings(collector), unreadable, classesScanned);
    }

    /** Findings that belong to a whole class rather than one instruction point here. */
    private static CallSite classSite(ArchiveWalker.ClassEntry entry) {
        return CallSite.ofClass(entry.archive(), entry.internalName());
    }

    private static String label(ArchiveWalker.ClassEntry entry) {
        String name = entry.internalName() + ".class";
        return entry.archive() == null ? name : entry.archive() + "!" + name;
    }

    private static ZipEntry findDescriptor(ZipFile zip) {
        // Paper prefers paper-plugin.yml when a jar ships both.
        ZipEntry paper = zip.getEntry(DescriptorParser.PAPER_DESCRIPTOR);
        return paper != null ? paper : zip.getEntry(DescriptorParser.BUKKIT_DESCRIPTOR);
    }

    private static PluginDescriptor readDescriptor(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            return DescriptorParser.parse(entry.getName(), in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }
}
