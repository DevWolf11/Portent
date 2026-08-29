package dev.portent.cli;

import dev.portent.fetch.ApiResolver;
import dev.portent.fetch.ArtifactCache;
import dev.portent.fetch.ArtifactFetcher;
import dev.portent.fetch.HttpTransport;
import dev.portent.fetch.MavenRepository;
import dev.portent.index.ApiIndex;
import dev.portent.index.Completeness;
import dev.portent.index.IndexBuilder;
import dev.portent.index.IndexIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "index",
        description =
                "Build a target API index, either from jars you have or by fetching them for a"
                        + " Minecraft version.")
public final class IndexCommand implements Callable<Integer> {

    @Option(
            names = "--api-jar",
            paramLabel = "<paper-api.jar>",
            description =
                    "Local API jar to index. Repeat it to add jars that complete the API's type"
                            + " hierarchies. The first jar is the API proper. Omit to fetch instead.")
    List<Path> apiJars = new ArrayList<>();

    @Option(names = "--out", required = true, paramLabel = "<index.json>",
            description = "Where to write the index.")
    Path out;

    @Option(
            names = "--minecraft-version",
            paramLabel = "<version>",
            description =
                    "Target server version, e.g. 26.1.2. Without --api-jar this is fetched from"
                            + " Maven along with the dependencies its hierarchies need.")
    String minecraftVersion;

    @Option(
            names = "--java-version",
            paramLabel = "<release>",
            description = "Java release the target runs on. Inferred from class files when omitted.")
    int javaVersion;

    @Option(names = "--cache-dir", paramLabel = "<dir>",
            description = "Where fetched artifacts are cached. Default: ~/.portent/cache")
    Path cacheDir;

    @Option(names = "--offline", description = "Never reach the network; use only cached artifacts.")
    boolean offline;

    @Option(names = "--repository", paramLabel = "<url>",
            description = "Extra Maven repository to search, before the defaults. Repeatable.")
    List<String> repositories = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        List<Path> jars;
        try {
            jars = apiJars.isEmpty() ? fetch() : apiJars;
        } catch (IOException e) {
            System.err.println("portent: " + e.getMessage());
            if (isNetworkFailure(e)) {
                System.err.println(
                        "portent: if this machine cannot reach Maven, download the API jar"
                                + " elsewhere and pass it with --api-jar, or use --offline to build"
                                + " from artifacts already cached.");
            }
            return ExitCode.USAGE;
        }

        ApiIndex index;
        try {
            index = IndexBuilder.fromJars(jars, minecraftVersion, javaVersion);
        } catch (IOException e) {
            System.err.println("portent: " + e.getMessage());
            return ExitCode.USAGE;
        }
        IndexIo.write(index, out);
        report(index, jars.size());
        return ExitCode.OK;
    }

    private static boolean isNetworkFailure(IOException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("could not reach") || message.contains("HTTP ");
    }

    private List<Path> fetch() throws IOException {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IOException(
                    "give either --api-jar for a jar you have, or --minecraft-version to fetch one");
        }
        List<MavenRepository> repos = new ArrayList<>();
        for (String url : repositories) {
            repos.add(new MavenRepository("custom", url));
        }
        repos.add(MavenRepository.PAPER);
        repos.add(MavenRepository.CENTRAL);

        ArtifactCache cache =
                new ArtifactCache(cacheDir != null ? cacheDir : ArtifactCache.defaultRoot());
        ArtifactFetcher fetcher =
                new ArtifactFetcher(repos, cache, new HttpTransport(), offline);

        System.out.printf("Fetching paper-api %s ...%n", minecraftVersion);
        ApiResolver.Result result = new ApiResolver(fetcher).resolvePaperApi(minecraftVersion);
        System.out.printf(
                "Resolved %d jar(s) into %s%n", result.resolved().size(), cache.root());
        for (String unresolved : result.unresolved()) {
            System.out.printf("  could not fetch %s%n", unresolved);
        }
        return result.resolved();
    }

    private void report(ApiIndex index, int jarCount) {
        System.out.printf(
                "Indexed %d types from %d jar(s) -> %s%n", index.typeCount(), jarCount, out);
        System.out.printf(
                "Target: %s, Java %s%n",
                index.minecraftVersion() == null ? "unknown" : index.minecraftVersion(),
                index.javaVersion() > 0 ? index.javaVersion() : "unknown");

        Completeness completeness = Completeness.of(index);
        System.out.printf(
                "Hierarchy completeness: %.1f%% of types have an unseen supertype%n",
                completeness.incompletePercent());
        if (completeness.isConcerning()) {
            // Silence here would mean scans quietly under-report; better to name what is missing.
            System.out.println(
                    "Warning: this index is missing types that scans need. Members inherited from"
                            + " the packages below cannot be resolved, so real breakage may go"
                            + " unreported. Add the jars with --api-jar.");
            completeness.missingSupertypes().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> System.out.printf("  %-58s reached by %d types%n", e.getKey(), e.getValue()));
        }
        if (index.minecraftVersion() == null) {
            System.out.println(
                    "Note: no target version found. Pass --minecraft-version so that"
                            + " version-dependent findings can be reported as errors.");
        }
    }
}
