package dev.portent.fetch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the jars an index needs for one server version: the API itself plus the dependencies
 * that complete its type hierarchies.
 *
 * <p>This is deliberately not a Maven implementation. It resolves the compile classpath one POM at
 * a time, applying parent POMs, {@code ${property}} substitution and imported BOMs, because that is
 * what it takes to reach Adventure from paper-api. Anything it cannot resolve is reported rather
 * than guessed at, and the index command prints hierarchy completeness afterwards so a gap shows up
 * as a number instead of as a silently worse scan.
 */
public final class ApiResolver {

    /** paper-api's coordinates. The group changed when Paper reorganised; both are tried. */
    private static final String[] PAPER_GROUPS = {"io.papermc.paper", "com.destroystokyo.paper"};

    private static final String PAPER_ARTIFACT = "paper-api";

    /** Deep enough for paper-api -> adventure-api -> adventure-key/examination. */
    private static final int MAX_DEPTH = 4;

    private final ArtifactFetcher fetcher;

    /**
     * @param resolved every jar found, API first
     * @param unresolved coordinates whose jar could not be fetched
     */
    public record Result(List<Path> resolved, List<String> unresolved, MavenCoordinate api) {}

    public ApiResolver(ArtifactFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** Resolves paper-api for a Minecraft version, trying the usual version suffixes. */
    public Result resolvePaperApi(String minecraftVersion) throws IOException {
        List<String> candidates =
                List.of(minecraftVersion, minecraftVersion + "-R0.1-SNAPSHOT");
        for (String group : PAPER_GROUPS) {
            for (String version : candidates) {
                MavenCoordinate coordinate = new MavenCoordinate(group, PAPER_ARTIFACT, version);
                Path jar = fetcher.fetch(coordinate, "jar");
                if (jar != null) {
                    return resolveFrom(coordinate, jar);
                }
            }
        }
        // Naming versions that do exist beats telling someone their version does not.
        List<String> available = publishedVersions();
        StringBuilder message = new StringBuilder();
        message.append("could not find ")
                .append(PAPER_ARTIFACT)
                .append(" for Minecraft ")
                .append(minecraftVersion)
                .append(".");
        if (!available.isEmpty()) {
            message.append(" Recently published: ").append(String.join(", ", available)).append(".");
        }
        message.append(
                " Experimental and pre-release builds are often not published to Maven at all;"
                        + " for those, download the API jar and pass it with --api-jar.");
        throw new IOException(message.toString());
    }

    /** The most recent versions the repository lists, newest last. Empty if it cannot be read. */
    private List<String> publishedVersions() {
        for (String group : PAPER_GROUPS) {
            try {
                Path metadata =
                        fetcher.fetchMetadata(group.replace('.', '/') + "/" + PAPER_ARTIFACT);
                if (metadata == null) {
                    continue;
                }
                String xml = Files.readString(metadata, StandardCharsets.UTF_8);
                List<String> versions = new ArrayList<>();
                for (String block : Xml.blocks(xml, "versions")) {
                    for (String version : Xml.blocks(block, "version")) {
                        String trimmed = version.trim();
                        if (!trimmed.isEmpty()) {
                            versions.add(trimmed);
                        }
                    }
                }
                if (!versions.isEmpty()) {
                    return versions.subList(Math.max(0, versions.size() - 6), versions.size());
                }
            } catch (IOException | RuntimeException e) {
                // Best effort: a better message is not worth failing over.
            }
        }
        return List.of();
    }
    /**
     * Adds jars until the API's type hierarchies are complete, and no further.
     *
     * <p>The obvious approach -- resolve the POM graph and fetch everything on the compile
     * classpath -- pulls in the world. Against paper-api 1.21.4 it fetched 32 jars and 19,080
     * types: all of Guava, Maven's resolver, even JUnit and AssertJ, because a scope declared two
     * POMs away is easy to get wrong and a correct answer would still include Guava.
     *
     * <p>None of that helps. A jar is only worth having if it defines a supertype that paper-api's
     * own types inherit from and the index cannot otherwise see. So each candidate is fetched,
     * checked against the outstanding gaps, and kept only if it closes one; the walk descends only
     * through jars that were kept, and stops as soon as nothing is missing.
     */
    private Result resolveFrom(MavenCoordinate api, Path apiJar) throws IOException {
        List<Path> jars = new ArrayList<>();
        jars.add(apiJar);
        List<String> unresolved = new ArrayList<>();

        Set<String> missing = Supertypes.unresolvedIn(jars);
        Set<String> visited = new HashSet<>();
        visited.add(api.versionlessId());

        Deque<Step> frontier = new ArrayDeque<>();
        frontier.add(new Step(api, 0));

        while (!frontier.isEmpty() && !missing.isEmpty()) {
            Step step = frontier.removeFirst();
            if (step.depth() >= MAX_DEPTH) {
                continue;
            }
            for (MavenCoordinate candidate : dependenciesOf(step.coordinate(), visited, unresolved)) {
                if (missing.isEmpty()) {
                    break;
                }
                Path jar = fetcher.fetch(candidate, "jar");
                if (jar == null) {
                    unresolved.add(candidate.toString());
                    continue;
                }
                if (!Supertypes.definesAnyOf(jar, missing)) {
                    // Fetched, inspected, not needed. Its own dependencies cannot be needed either.
                    continue;
                }
                jars.add(jar);
                missing = Supertypes.unresolvedIn(jars);
                frontier.add(new Step(candidate, step.depth() + 1));
            }
        }
        return new Result(List.copyOf(jars), List.copyOf(unresolved), api);
    }

    /** The consumer-classpath dependencies of one artifact, with versions resolved. */
    private List<MavenCoordinate> dependenciesOf(
            MavenCoordinate coordinate, Set<String> visited, List<String> unresolved)
            throws IOException {
        Pom pom = readPom(coordinate);
        if (pom == null) {
            return List.of();
        }
        Map<String, String> properties = propertiesOf(pom);
        Map<String, String> managed = managedVersions(pom, properties);

        List<MavenCoordinate> found = new ArrayList<>();
        for (Pom.Dependency dependency : pom.dependencies()) {
            if (!dependency.isOnConsumerClasspath()) {
                continue;
            }
            String key = dependency.groupId() + ":" + dependency.artifactId();
            if (!visited.add(key)) {
                continue;
            }
            String version =
                    substitute(
                            dependency.version() != null ? dependency.version() : managed.get(key),
                            properties);
            if (version == null || version.contains("${")) {
                unresolved.add(key + " (no resolvable version)");
                continue;
            }
            found.add(new MavenCoordinate(dependency.groupId(), dependency.artifactId(), version));
        }
        return found;
    }

    /** Properties from this POM and its parents, nearest first. */
    private Map<String, String> propertiesOf(Pom pom) throws IOException {
        Map<String, String> properties = new LinkedHashMap<>(pom.properties());
        Pom current = pom;
        for (int i = 0; i < MAX_DEPTH && current.parent() != null; i++) {
            Pom parent = readPom(current.parent());
            if (parent == null) {
                break;
            }
            parent.properties().forEach(properties::putIfAbsent);
            current = parent;
        }
        return properties;
    }

    /** Versions from dependencyManagement, following imported BOMs. */
    private Map<String, String> managedVersions(Pom pom, Map<String, String> properties)
            throws IOException {
        Map<String, String> versions = new LinkedHashMap<>();
        for (Pom.Dependency entry : pom.managed()) {
            String version = substitute(entry.version(), properties);
            if (version == null) {
                continue;
            }
            if (entry.isBomImport()) {
                Pom bom = readPom(new MavenCoordinate(entry.groupId(), entry.artifactId(), version));
                if (bom != null) {
                    for (Pom.Dependency managed : bom.managed()) {
                        String bomVersion =
                                substitute(managed.version(), propertiesOf(bom));
                        if (bomVersion != null) {
                            versions.putIfAbsent(
                                    managed.groupId() + ":" + managed.artifactId(), bomVersion);
                        }
                    }
                }
                continue;
            }
            versions.putIfAbsent(entry.groupId() + ":" + entry.artifactId(), version);
        }
        return versions;
    }

    private Pom readPom(MavenCoordinate coordinate) throws IOException {
        Path path = fetcher.fetch(coordinate, "pom");
        if (path == null) {
            return null;
        }
        return Pom.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    static String substitute(String value, Map<String, String> properties) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        String out = value;
        for (int i = 0; i < 5 && out.contains("${"); i++) {
            for (Map.Entry<String, String> property : properties.entrySet()) {
                out = out.replace("${" + property.getKey() + "}", property.getValue());
            }
            if (!out.contains("${")) {
                break;
            }
        }
        return out;
    }

    private record Step(MavenCoordinate coordinate, int depth) {}
}
