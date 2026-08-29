package dev.plugindoctor.scan;

import dev.plugindoctor.index.ApiIndex;
import dev.plugindoctor.model.PluginReport;
import dev.plugindoctor.model.ScanReport;
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

    public PluginsFolderScanner(ApiIndex index) {
        this.index = index;
    }

    public ScanReport scan(Path pluginsDirectory) throws IOException {
        if (!Files.isDirectory(pluginsDirectory)) {
            throw new IOException("not a directory: " + pluginsDirectory);
        }
        JarScanner scanner = new JarScanner(new MemberResolver(index));
        List<PluginReport> reports = new ArrayList<>();
        for (Path jar : jarsIn(pluginsDirectory)) {
            reports.add(scanner.scan(jar));
        }
        return new ScanReport(
                pluginsDirectory.toString(), index.source(), index.typeCount(), reports);
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
