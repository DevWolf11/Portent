package dev.portent.model;

import java.util.List;

/**
 * The outcome for a whole plugins folder.
 *
 * @param pluginsDirectory the folder that was scanned, for the report header
 * @param indexSource the API jar the index was built from
 * @param indexTypeCount how many types the index describes
 * @param plugins one entry per jar, in file-name order
 */
public record ScanReport(
        String pluginsDirectory,
        String indexSource,
        String minecraftVersion,
        int javaVersion,
        int indexTypeCount,
        List<PluginReport> plugins) {

    public ScanReport {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    /** True if any plugin will not work on the target. Drives the process exit code. */
    public boolean hasBlockingFindings() {
        return count(Verdict.RED) > 0;
    }

    public long count(Verdict verdict) {
        return plugins.stream().filter(p -> p.verdict() == verdict).count();
    }
}
