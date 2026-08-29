package dev.portent.scan;

import dev.portent.index.ApiIndex;
import dev.portent.model.PluginReport;
import dev.portent.model.ScanReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Scans every jar in a plugins folder. */
public final class PluginsFolderScanner {

    private final ApiIndex index;
    private final List<Suppression> suppressions;
    private List<Suppression> unusedSuppressions = List.of();

    public PluginsFolderScanner(ApiIndex index) {
        this(index, List.of());
    }

    public PluginsFolderScanner(ApiIndex index, List<Suppression> suppressions) {
        this.index = index;
        this.suppressions = suppressions == null ? List.of() : List.copyOf(suppressions);
    }

    /** Suppressions that matched nothing in this scan; stale entries worth pruning. */
    public List<Suppression> unusedSuppressions() {
        return unusedSuppressions;
    }

    public ScanReport scan(Path pluginsDirectory) throws IOException {
        if (!Files.isDirectory(pluginsDirectory)) {
            throw new IOException("not a directory: " + pluginsDirectory);
        }
        JarScanner scanner = new JarScanner(index, suppressions);
        List<PluginReport> reports = new ArrayList<>();
        for (Path jar : jarsIn(pluginsDirectory)) {
            reports.add(scanner.scan(jar));
        }
        unusedSuppressions = scanner.unusedSuppressions();
        return new ScanReport(
                pluginsDirectory.toString(),
                index.source(),
                index.minecraftVersion(),
                index.javaVersion(),
                index.typeCount(),
                reports);
    }

    private static List<Path> jarsIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }
}
