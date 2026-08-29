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
import java.util.Enumeration;
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
        Set<String> ownTypes = ownTypes(zip);
        List<SymbolReference> references = new ArrayList<>();
        List<UnreadableClass> unreadable = new ArrayList<>();
        int classesScanned = 0;

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!isClassEntry(entry)) {
                continue;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                references.addAll(ReferenceCollector.collect(in, owner -> !ownTypes.contains(owner)));
                classesScanned++;
            } catch (IOException | RuntimeException e) {
                // A malformed class is the plugin's problem, not a reason to abandon the jar.
                unreadable.add(new UnreadableClass(entry.getName(), message(e)));
            }
        }
        return PluginReport.scanned(
                fileName, descriptor, findings(references), unreadable, classesScanned);
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

    /** Internal names of classes the jar ships itself, so shaded Bukkit-named types are ignored. */
    private static Set<String> ownTypes(ZipFile zip) {
        Set<String> names = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (isClassEntry(entry)) {
                String name = entry.getName();
                names.add(name.substring(0, name.length() - ".class".length()));
            }
        }
        return names;
    }

    private static boolean isClassEntry(ZipEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        String name = entry.getName();
        return name.endsWith(".class")
                && !name.endsWith("module-info.class")
                && !name.endsWith("package-info.class");
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }

    private record SymbolKey(MemberKind kind, String owner, String name, String descriptor) {}
}
