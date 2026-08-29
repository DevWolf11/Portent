package dev.portent.cli;

import dev.portent.index.ApiIndex;
import dev.portent.index.IndexIo;
import dev.portent.model.ScanReport;
import dev.portent.report.JsonReport;
import dev.portent.report.TextReport;
import dev.portent.scan.PluginsFolderScanner;
import dev.portent.scan.Suppression;
import dev.portent.scan.SuppressionFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "scan", description = "Check every plugin in a folder against a target API index.")
public final class ScanCommand implements Callable<Integer> {

    /** Output shapes. */
    public enum Format {
        text,
        json
    }

    @Option(names = "--plugins", required = true, paramLabel = "<dir>",
            description = "The server's plugins folder.")
    Path plugins;

    @Option(names = "--index", required = true, paramLabel = "<index.json>",
            description = "Index produced by `portent index`.")
    Path index;

    @Option(names = "--format", paramLabel = "<text|json>", description = "Output format. Default: text.")
    Format format = Format.text;

    @Option(
            names = "--suppressions",
            paramLabel = "<suppressions.yml>",
            description =
                    "Findings to accept, each with a mandatory reason. Suppressed findings are"
                            + " excluded from the verdict but still counted in the report.")
    Path suppressionsFile;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(plugins)) {
            System.err.println("portent: not a directory: " + plugins);
            return ExitCode.USAGE;
        }
        if (!Files.isRegularFile(index)) {
            System.err.println("portent: no such index: " + index);
            return ExitCode.USAGE;
        }

        ApiIndex apiIndex;
        try {
            apiIndex = IndexIo.read(index);
        } catch (IOException e) {
            System.err.println("portent: " + e.getMessage());
            return ExitCode.USAGE;
        }

        List<Suppression> suppressions = List.of();
        if (suppressionsFile != null) {
            if (!Files.isRegularFile(suppressionsFile)) {
                System.err.println("portent: no such suppressions file: " + suppressionsFile);
                return ExitCode.USAGE;
            }
            try {
                suppressions = SuppressionFile.read(suppressionsFile);
            } catch (IOException e) {
                System.err.println("portent: " + e.getMessage());
                return ExitCode.USAGE;
            }
        }

        PluginsFolderScanner scanner = new PluginsFolderScanner(apiIndex, suppressions);
        ScanReport report = scanner.scan(plugins);
        System.out.print(
                format == Format.json ? JsonReport.render(report) + "\n" : TextReport.render(report));

        // A suppression that no longer matches is usually one the API outgrew.
        for (Suppression stale : scanner.unusedSuppressions()) {
            System.err.println(
                    "portent: suppression matched nothing, consider removing it: "
                            + (stale.symbol() != null ? stale.symbol() : stale.plugin()));
        }

        // Warnings alone do not fail a build; a plugin that will not work does.
        return report.hasBlockingFindings() ? ExitCode.FINDINGS : ExitCode.OK;
    }
}
