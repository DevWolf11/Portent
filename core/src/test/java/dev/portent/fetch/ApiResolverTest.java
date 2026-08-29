package dev.portent.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dependency graph this has to walk is the real one: paper-api reaches Adventure through a
 * property-versioned BOM import, and Adventure reaches its own key and examination artifacts.
 */
class ApiResolverTest {

    @TempDir Path tempDir;

    private static final MavenCoordinate PAPER =
            new MavenCoordinate("io.papermc.paper", "paper-api", "26.1.2");
    private static final MavenCoordinate BOM =
            new MavenCoordinate("net.kyori", "adventure-bom", "5.2.0");
    private static final MavenCoordinate ADVENTURE =
            new MavenCoordinate("net.kyori", "adventure-api", "5.2.0");
    private static final MavenCoordinate KEY =
            new MavenCoordinate("net.kyori", "adventure-key", "5.2.0");

    @Test
    void followsPropertiesBomImportsAndTransitiveDependencies() throws IOException {
        ApiResolver.Result result = resolve();

        assertThat(result.resolved()).hasSize(3);
        assertThat(result.resolved().get(0).getFileName().toString())
                .as("the API jar must come first; it fixes the target metadata")
                .isEqualTo("paper-api-26.1.2.jar");
        assertThat(result.resolved().stream().map(p -> p.getFileName().toString()))
                .contains("adventure-api-5.2.0.jar", "adventure-key-5.2.0.jar");
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    void fetchesNothingItDoesNotNeed() throws IOException {
        // Against the real paper-api this once fetched 32 jars and 19,080 types -- all of Guava,
        // Maven's resolver, JUnit, AssertJ -- because it walked the whole compile closure. A jar
        // is only worth having if it defines a supertype the API's own types inherit from.
        ApiResolver.Result result = resolve();

        assertThat(result.resolved().stream().map(p -> p.getFileName().toString()))
                .noneMatch(name -> name.startsWith("guava") || name.startsWith("unrelated"));
    }

    @Test
    void skipsDependenciesThatAreNotOnAConsumerClasspath() throws IOException {
        // provided, test and optional dependencies are not present at runtime, so they cannot
        // complete a hierarchy and fetching them would only be slower.
        assertThat(resolve().resolved().stream().map(p -> p.getFileName().toString()))
                .noneMatch(name -> name.startsWith("junit") || name.startsWith("annotations"));
    }

    @Test
    void reportsWhatItCouldNotFetchRatherThanFailing() throws IOException {
        LocalRepository repo = repository();
        // Declare a dependency nobody published.
        repo.publishPom(
                ADVENTURE,
                pom(
                        ADVENTURE,
                        """
                        <dependencies>
                          <dependency>
                            <groupId>net.kyori</groupId><artifactId>missing</artifactId>
                            <version>9.9.9</version>
                          </dependency>
                        </dependencies>
                        """));

        ApiResolver.Result result = resolverFor(repo).resolvePaperApi("26.1.2");

        assertThat(result.unresolved()).anyMatch(s -> s.contains("missing"));
        assertThat(result.resolved()).isNotEmpty();
    }

    @Test
    void explainsAnUnknownVersionInsteadOfCrashing() throws IOException {
        assertThatThrownBy(() -> resolverFor(repository()).resolvePaperApi("99.9"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("could not find")
                .hasMessageContaining("--api-jar");
    }

    private ApiResolver.Result resolve() throws IOException {
        return resolverFor(repository()).resolvePaperApi("26.1.2");
    }

    private ApiResolver resolverFor(LocalRepository repo) {
        return new ApiResolver(
                new ArtifactFetcher(
                        List.of(repo.asRepository()),
                        new ArtifactCache(tempDir.resolve("cache-" + repo.hashCode())),
                        new HttpTransport(),
                        false));
    }

    private LocalRepository repository() throws IOException {
        LocalRepository repo = new LocalRepository(tempDir.resolve("repo"));

        repo.publish(
                PAPER,
                "jar",
                FixtureJars.jarBytes(
                        Map.of(
                                "org/bukkit/entity/Player.class",
                                Bytecode.apiInterface(
                                        "org/bukkit/entity/Player",
                                        List.of("net/kyori/adventure/audience/Audience")))));
        repo.publishPom(
                PAPER,
                pom(
                        PAPER,
                        """
                        <properties>
                          <adventure.version>5.2.0</adventure.version>
                        </properties>
                        <dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>net.kyori</groupId><artifactId>adventure-bom</artifactId>
                              <version>${adventure.version}</version>
                              <type>pom</type><scope>import</scope>
                            </dependency>
                          </dependencies>
                        </dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>net.kyori</groupId><artifactId>adventure-api</artifactId>
                          </dependency>
                          <dependency>
                            <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId>
                            <version>26.0.0</version><scope>provided</scope>
                          </dependency>
                          <dependency>
                            <groupId>junit</groupId><artifactId>junit</artifactId>
                            <version>4.13.1</version><scope>test</scope>
                          </dependency>
                        </dependencies>
                        """));

        repo.publishPom(
                BOM,
                pom(
                        BOM,
                        """
                        <dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>net.kyori</groupId><artifactId>adventure-api</artifactId>
                              <version>5.2.0</version>
                            </dependency>
                          </dependencies>
                        </dependencyManagement>
                        """));

        repo.publish(
                ADVENTURE,
                "jar",
                FixtureJars.jarBytes(
                        Map.of(
                                "net/kyori/adventure/audience/Audience.class",
                                Bytecode.apiInterface(
                                        "net/kyori/adventure/audience/Audience",
                                        List.of("net/kyori/adventure/key/Keyed")))));
        repo.publishPom(
                ADVENTURE,
                pom(
                        ADVENTURE,
                        """
                        <dependencies>
                          <dependency>
                            <groupId>net.kyori</groupId><artifactId>adventure-key</artifactId>
                            <version>5.2.0</version>
                          </dependency>
                        </dependencies>
                        """));

        repo.publish(KEY, "jar", classJar("net/kyori/adventure/key/Keyed"));
        repo.publishPom(KEY, pom(KEY, ""));

        // A perfectly ordinary compile dependency that defines nothing paper-api inherits from.
        MavenCoordinate unrelated = new MavenCoordinate("com.google.guava", "guava", "33.0.0");
        repo.publish(unrelated, "jar", classJar("com/google/common/collect/ImmutableList"));
        repo.publishPom(unrelated, pom(unrelated, ""));
        return repo;
    }

    private static String pom(MavenCoordinate coordinate, String body) {
        return """
               <project>
                 <groupId>%s</groupId>
                 <artifactId>%s</artifactId>
                 <version>%s</version>
                 %s
               </project>
               """
                .formatted(coordinate.groupId(), coordinate.artifactId(), coordinate.version(), body);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A jar defining one class, so the resolver can tell whether it closes a gap. */
    private static byte[] classJar(String internalName) throws IOException {
        return FixtureJars.jarBytes(
                Map.of(internalName + ".class", Bytecode.apiInterface(internalName, List.of())));
    }
}
