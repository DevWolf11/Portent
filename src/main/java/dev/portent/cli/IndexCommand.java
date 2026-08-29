package dev.portent.cli;

import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.index.IndexIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "index", description = "Build a target API index from a local Paper/Bukkit API jar.")
public final class IndexCommand implements Callable<Integer> {

    @Option(
            names = "--api-jar",
            required = true,
            paramLabel = "<paper-api.jar>",
            description = "Local API jar to index. Never downloaded - point this at a file you have.")
    Path apiJar;

    @Option(names = "--out", required = true, paramLabel = "<index.json>",
            description = "Where to write the index.")
    Path out;

    @Option(
            names = "--minecraft-version",
            paramLabel = "<version>",
            description =
                    "Target server version, e.g. 26.1. Read from the jar's Maven metadata when"
                            + " omitted. Without it, version-dependent findings stay warnings.")
    String minecraftVersion;

    @Option(
            names = "--java-version",
            paramLabel = "<release>",
            description =
                    "Java release the target server runs on. Inferred from the jar's class file"
                            + " versions when omitted.")
    int javaVersion;

    @Override
    public Integer call() throws Exception {
        ApiIndex index;
        try {
            index = IndexBuilder.fromJar(apiJar, minecraftVersion, javaVersion);
        } catch (IOException e) {
            System.err.println("portent: " + e.getMessage());
            return ExitCode.USAGE;
        }
        IndexIo.write(index, out);

        System.out.printf(
                "Indexed %d types from %s -> %s%n", index.typeCount(), apiJar.getFileName(), out);
        System.out.printf(
                "Target: %s, Java %s%n",
                index.minecraftVersion() == null ? "unknown" : index.minecraftVersion(),
                index.javaVersion() > 0 ? index.javaVersion() : "unknown");
        if (index.minecraftVersion() == null) {
            System.out.println(
                    "Note: no target version found. Pass --minecraft-version so that version-dependent"
                            + " findings can be reported as errors rather than warnings.");
        }
        return ExitCode.OK;
    }
}
