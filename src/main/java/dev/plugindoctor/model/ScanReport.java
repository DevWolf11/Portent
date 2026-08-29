package dev.plugindoctor.model;

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
        String pluginsDirectory, String indexSource, int indexTypeCount, List<PluginReport> plugins) {

    public ScanReport {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    public long count(Verdict verdict) {
        return plugins.stream().filter(p -> p.verdict() == verdict).count();
    }
}
