package dev.portent.scan;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.model.PluginReport;
import dev.portent.model.ScanReport;
import dev.portent.model.Verdict;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppressionTest {

    @TempDir Path tempDir;

    private static final String SYMBOL =
            "org/bukkit/entity/Player#setDisplayName(Ljava/lang/String;)V";

    @Test
    void aSuppressedErrorNoLongerDrivesTheVerdict() throws IOException {
        PluginReport report =
                scan(
                        List.of(
                                new Suppression(
                                        "Broken", "MISSING_METHOD", SYMBOL, "version-gated, never loaded")));

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void aSuppressedFindingIsStillRecorded() throws IOException {
        // Suppression must not make breakage disappear without a trace.
        PluginReport report =
                scan(List.of(new Suppression("Broken", null, SYMBOL, "accepted for now")));

        assertThat(report.suppressedFindings()).hasSize(1);
        assertThat(report.suppressedFindings().getFirst().subject()).isEqualTo(SYMBOL);
    }

    @Test
    void matchesOnJarFileNameAsWellAsPluginName() throws IOException {
        assertThat(scan(List.of(new Suppression("broken.jar", null, SYMBOL, "by file name"))).findings())
                .isEmpty();
    }

    @Test
    void doesNotSuppressADifferentPlugin() throws IOException {
        PluginReport report =
                scan(List.of(new Suppression("SomeOtherPlugin", null, SYMBOL, "different plugin")));

        assertThat(report.findings()).hasSize(1);
        assertThat(report.verdict()).isEqualTo(Verdict.RED);
    }

    @Test
    void reportsSuppressionsThatMatchedNothing() throws IOException {
        // Stale entries outlive the breakage they were written for.
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        pluginJar(plugins);
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), "26.1", 25);

        Suppression stale = new Suppression("Broken", null, "org/bukkit/Gone#gone()V", "old");
        PluginsFolderScanner scanner =
                new PluginsFolderScanner(
                        index, List.of(stale, new Suppression("Broken", null, SYMBOL, "current")));
        ScanReport report = scanner.scan(plugins);

        assertThat(report.count(Verdict.GREEN)).isEqualTo(1);
        assertThat(scanner.unusedSuppressions()).containsExactly(stale);
    }

    @Test
    void aSuppressionWithoutAReasonIsRejected() {
        assertThatThrownBy(() -> new Suppression("Broken", null, SYMBOL, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a reason");
    }

    @Test
    void aSuppressionMatchingEverythingIsRejected() {
        assertThatThrownBy(() -> new Suppression(null, null, null, "silence everything"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must narrow");
    }

    @Test
    void parsesTheFileFormat() throws IOException {
        String yaml =
                """
                suppressions:
                  - plugin: ViaVersion
                    finding: MISSING_METHOD
                    symbol: org/bukkit/block/Block#getTypeId()I
                    reason: version-gated; only loaded on 1.8/1.9 servers
                  - plugin: OldPlugin
                    reason: retiring this one anyway
                """;

        List<Suppression> parsed =
                SuppressionFile.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(parsed).hasSize(2);
        assertThat(parsed.getFirst().plugin()).isEqualTo("ViaVersion");
        assertThat(parsed.getFirst().type()).isEqualTo("MISSING_METHOD");
        assertThat(parsed.getFirst().symbol()).isEqualTo("org/bukkit/block/Block#getTypeId()I");
        assertThat(parsed.get(1).symbol()).isNull();
    }

    @Test
    void rejectsAFileMissingItsReasons() {
        String yaml = "suppressions:\n  - plugin: ViaVersion\n    symbol: a#b()V\n";

        assertThatThrownBy(
                        () ->
                                SuppressionFile.parse(
                                        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("needs a reason");
    }

    private PluginReport scan(List<Suppression> suppressions) throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), "26.1", 25);
        Path plugins = Files.createDirectories(tempDir.resolve("p" + suppressions.hashCode()));
        pluginJar(plugins);
        return new PluginsFolderScanner(index, suppressions).scan(plugins).plugins().getFirst();
    }

    private void pluginJar(Path dir) throws IOException {
        FixtureJars.pluginJar(
                dir,
                "broken.jar",
                "Broken",
                Map.of(
                        "com/example/b/Nick",
                        Bytecode.pluginClass(
                                "com/example/b/Nick",
                                callInterface(
                                        FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))));
    }
}
