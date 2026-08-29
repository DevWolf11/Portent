package dev.plugindoctor.cli;

import dev.plugindoctor.index.ApiIndex;
import dev.plugindoctor.index.IndexBuilder;
import dev.plugindoctor.index.IndexIo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "index",
        description = "Build a target API index from a local Paper/Bukkit API jar.")
public final class IndexCommand implements Callable<Integer> {

    @Option(
            names = "--api-jar",
            required = true,
            paramLabel = "<paper-api.jar>",
            description = "Local API jar to index. Never downloaded — point this at a file you have.")
    Path apiJar;

    @Option(
            names = "--out",
            required = true,
            paramLabel = "<index.json>",
            description = "Where to write the index.")
    Path out;

    @Override
    public Integer call() throws Exception {
        if (!Files.isRegularFile(apiJar)) {
            System.err.println("plugin-doctor: no such API jar: " + apiJar);
            return 1;
        }
        ApiIndex index = IndexBuilder.fromJar(apiJar);
        IndexIo.write(index, out);
        System.out.printf(
                "Indexed %d types from %s -> %s%n", index.typeCount(), apiJar.getFileName(), out);
        return 0;
    }
}
