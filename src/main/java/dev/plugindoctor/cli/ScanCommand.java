package dev.plugindoctor.cli;

import dev.plugindoctor.index.ApiIndex;
import dev.plugindoctor.index.IndexIo;
import dev.plugindoctor.model.ScanReport;
import dev.plugindoctor.report.TextReport;
import dev.plugindoctor.scan.PluginsFolderScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "scan",
        description = "Check every plugin in a folder against a target API index.")
public final class ScanCommand implements Callable<Integer> {

    @Option(
            names = "--plugins",
            required = true,
            paramLabel = "<dir>",
            description = "The server's plugins folder.")
    Path plugins;

    @Option(
            names = "--index",
            required = true,
            paramLabel = "<index.json>",
            description = "Index produced by `plugin-doctor index`.")
    Path index;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(plugins)) {
            System.err.println("plugin-doctor: not a directory: " + plugins);
            return 1;
        }
        if (!Files.isRegularFile(index)) {
            System.err.println("plugin-doctor: no such index: " + index);
            return 1;
        }
        ApiIndex apiIndex = IndexIo.read(index);
        ScanReport report = new PluginsFolderScanner(apiIndex).scan(plugins);
        System.out.print(TextReport.render(report));
        return 0;
    }
}
