package dev.plugindoctor.scan;

import dev.plugindoctor.model.CallSite;
import dev.plugindoctor.model.Finding;
import dev.plugindoctor.model.FindingType;
import dev.plugindoctor.model.MemberKind;
import dev.plugindoctor.model.PluginReport;
import dev.plugindoctor.model.UnreadableClass;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Scans a single jar against a target API index. */
public final class JarScanner {

    private final MemberResolver resolver;

    public JarScanner(MemberResolver resolver) {
        this.resolver = resolver;
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
                return PluginReport.skipped(
                        fileName, "unreadable " + descriptorEntry.getName());
            }
            return scanClasses(fileName, zip, descriptor);
        } catch (IOException e) {
            return PluginReport.skipped(fileName, "not a readable jar: " + message(e));
        }
    }

    private PluginReport scanClasses(String fileName, ZipFile zip, PluginDescriptor descriptor) {
        ArchiveWalker.Contents contents = ArchiveWalker.collect(zip);

        // Bukkit-named classes the plugin ships itself are present at runtime, so a reference to
        // one says nothing about the server version.
        Set<String> ownTypes = new HashSet<>();
        for (ArchiveWalker.ClassEntry entry : contents.classes()) {
            ownTypes.add(entry.internalName());
        }

        List<SymbolReference> references = new ArrayList<>();
        List<UnreadableClass> unreadable = new ArrayList<>(contents.unreadable());
        int classesScanned = 0;

        for (ArchiveWalker.ClassEntry entry : contents.classes()) {
            try {
                references.addAll(
                        ReferenceCollector.collect(
                                entry.bytes(), entry.archive(), owner -> !ownTypes.contains(owner)));
                classesScanned++;
            } catch (RuntimeException e) {
                // A malformed class is the plugin's problem, not a reason to abandon the jar.
                unreadable.add(new UnreadableClass(label(entry), message(e)));
            }
        }
        return PluginReport.scanned(
                fileName, descriptor, findings(references), unreadable, classesScanned);
    }

    private static String label(ArchiveWalker.ClassEntry entry) {
        String name = entry.internalName() + ".class";
        return entry.archive() == null ? name : entry.archive() + "!" + name;
    }

    /** Group every absent reference into one finding per symbol, carrying all its call sites. */
    private List<Finding> findings(List<SymbolReference> references) {
        Map<SymbolKey, Set<CallSite>> absent = new LinkedHashMap<>();
        Map<SymbolKey, Resolution> decided = new LinkedHashMap<>();

        for (SymbolReference reference : references) {
            SymbolKey key =
                    new SymbolKey(
                            reference.kind(), reference.owner(), reference.name(), reference.descriptor());
            Resolution resolution =
                    decided.computeIfAbsent(key, k -> resolve(reference));
            if (resolution == Resolution.ABSENT) {
                absent.computeIfAbsent(key, k -> new TreeSet<>()).add(reference.callSite());
            }
        }

        List<Finding> findings = new ArrayList<>(absent.size());
        for (Map.Entry<SymbolKey, Set<CallSite>> entry : absent.entrySet()) {
            SymbolKey key = entry.getKey();
            FindingType type =
                    key.kind() == MemberKind.METHOD
                            ? FindingType.MISSING_METHOD
                            : FindingType.MISSING_FIELD;
            findings.add(
                    new Finding(
                            type,
                            key.kind(),
                            key.owner(),
                            key.name(),
                            key.descriptor(),
                            List.copyOf(entry.getValue())));
        }
        findings.sort(
                java.util.Comparator.comparing(Finding::owner)
                        .thenComparing(Finding::memberName)
                        .thenComparing(Finding::descriptor));
        return findings;
    }

    private Resolution resolve(SymbolReference reference) {
        return reference.kind() == MemberKind.METHOD
                ? resolver.resolveMethod(reference.owner(), reference.name(), reference.descriptor())
                : resolver.resolveField(reference.owner(), reference.name(), reference.descriptor());
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

    private record SymbolKey(MemberKind kind, String owner, String name, String descriptor) {}
}
