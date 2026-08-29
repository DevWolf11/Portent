package dev.portent.plugin;

import dev.portent.fetch.ApiResolver;
import dev.portent.fetch.ArtifactCache;
import dev.portent.fetch.ArtifactFetcher;
import dev.portent.fetch.HttpTransport;
import dev.portent.fetch.MavenRepository;
import dev.portent.index.ApiIndex;
import dev.portent.index.Completeness;
import dev.portent.index.IndexBuilder;
import dev.portent.index.IndexIo;
import dev.portent.model.PluginReport;
import dev.portent.model.ScanReport;
import dev.portent.model.Verdict;
import dev.portent.report.TextReport;
import dev.portent.scan.PluginsFolderScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;

/**
 * Runs one check off the main thread and reports back on it.
 *
 * <p>The full report is far too long for a chat window, so it is written to a file and the sender
 * gets a verdict per plugin. The detail is what an admin reads afterwards; the summary is what they
 * need in the moment.
 */
final class CheckRunner {

    private final PortentPlugin plugin;

    /** One check at a time: two concurrent scans would fight over the same cache directory. */
    private final AtomicBoolean running = new AtomicBoolean();

    CheckRunner(PortentPlugin plugin) {
        this.plugin = plugin;
    }

    void startAsync(CommandSender sender, String version) {
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("A check is already running. Wait for it to finish.");
            return;
        }
        sender.sendMessage("Checking your plugins against Minecraft " + version + "...");
        sender.sendMessage("This downloads the API for that version the first time, so give it a moment.");

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<String> output = new ArrayList<>();
                            try {
                                output.addAll(check(version));
                            } catch (IOException | RuntimeException e) {
                                plugin.getLogger().log(Level.WARNING, "Portent check failed", e);
                                output.add("Check failed: " + e.getMessage());
                                output.add("If this server cannot reach the internet, build an index"
                                        + " elsewhere and drop it in " + plugin.getDataFolder().getName() + "/");
                            } finally {
                                running.set(false);
                            }
                            // Back to the main thread before touching the sender again.
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> output.forEach(sender::sendMessage));
                        });
    }

    private List<String> check(String version) throws IOException {
        Path dataFolder = plugin.getDataFolder().toPath();
        Files.createDirectories(dataFolder);

        ApiIndex index = indexFor(version, dataFolder);
        Completeness completeness = Completeness.of(index);

        // The server's plugins folder is this plugin's parent directory.
        Path pluginsFolder = dataFolder.getParent();
        ScanReport report = new PluginsFolderScanner(index).scan(pluginsFolder);

        Path reportFile = dataFolder.resolve("report-" + version + ".txt");
        Files.write(reportFile, TextReport.render(report).getBytes(StandardCharsets.UTF_8));

        return summarise(report, completeness, reportFile);
    }

    /** Reuses an index already built for this version; builds one if there is none. */
    private ApiIndex indexFor(String version, Path dataFolder) throws IOException {
        Path indexFile = dataFolder.resolve("index-" + version + ".json");
        if (Files.isRegularFile(indexFile)) {
            try {
                return IndexIo.read(indexFile);
            } catch (IOException e) {
                plugin.getLogger().info("Rebuilding " + indexFile.getFileName() + ": " + e.getMessage());
            }
        }

        ArtifactFetcher fetcher =
                new ArtifactFetcher(
                        Arrays.asList(MavenRepository.PAPER, MavenRepository.CENTRAL),
                        new ArtifactCache(dataFolder.resolve("cache")),
                        new HttpTransport(),
                        false);
        ApiResolver.Result resolved = new ApiResolver(fetcher).resolvePaperApi(version);
        ApiIndex index = IndexBuilder.fromJars(resolved.resolved(), version, 0);
        IndexIo.write(index, indexFile);
        return index;
    }

    private List<String> summarise(
            ScanReport report, Completeness completeness, Path reportFile) {
        List<String> lines = new ArrayList<>();
        lines.add("Portent: " + report.plugins().size() + " jar(s) checked against "
                + report.minecraftVersion());

        for (PluginReport pluginReport : report.plugins()) {
            if (pluginReport.verdict() == Verdict.SKIPPED) {
                continue;
            }
            // Portent's own jar is in the folder too, and reporting on itself is just noise.
            if ("Portent".equalsIgnoreCase(nameOf(pluginReport))) {
                continue;
            }
            lines.add(
                    "  "
                            + pluginReport.verdict()
                            + "  "
                            + pluginReport.display()
                            + errorSuffix(pluginReport));
        }

        lines.add(
                report.count(Verdict.RED)
                        + " will not work, "
                        + report.count(Verdict.YELLOW)
                        + " need a look, "
                        + report.count(Verdict.GREEN)
                        + " are fine.");
        if (completeness.isConcerning()) {
            lines.add("Warning: the index for this version is incomplete, so some breakage may be"
                    + " missing from this report.");
        }
        lines.add("Full detail: " + reportFile);
        return lines;
    }

    private static String nameOf(PluginReport report) {
        return report.descriptor() == null ? "" : String.valueOf(report.descriptor().name());
    }

    private static String errorSuffix(PluginReport report) {
        long errors = report.findings().stream().filter(f -> f.severity().name().equals("ERROR")).count();
        long warnings = report.findings().size() - errors;
        if (errors > 0) {
            return "  (" + errors + " breaking, " + warnings + " warnings)";
        }
        return warnings > 0 ? "  (" + warnings + " warnings)" : "";
    }
}
