package dev.portent.cli;

import dev.portent.index.ApiIndex;
import dev.portent.index.IndexIo;
import dev.portent.model.ScanReport;
import dev.portent.report.JsonReport;
import dev.portent.report.TextReport;
import dev.portent.scan.PluginsFolderScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        ScanReport report = new PluginsFolderScanner(apiIndex).scan(plugins);
        System.out.print(
                format == Format.json ? JsonReport.render(report) + "\n" : TextReport.render(report));

        // Warnings alone do not fail a build; a plugin that will not work does.
        return report.hasBlockingFindings() ? ExitCode.FINDINGS : ExitCode.OK;
    }
}
