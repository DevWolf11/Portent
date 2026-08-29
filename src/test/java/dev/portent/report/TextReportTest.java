package dev.portent.report;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static dev.portent.fixtures.Bytecode.Reference.readStaticField;
import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.model.ScanReport;
import dev.portent.model.Verdict;
import dev.portent.scan.PluginsFolderScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextReportTest {

    @TempDir Path tempDir;

    @Test
    void rendersOneGreenAndOneRedPlugin() throws IOException {
        ScanReport report = scanFolderWithOneGreenAndOneRed();
        String text = TextReport.render(report);

        assertThat(text).contains("GREEN  QuietPlugin 1.0.0  (quiet.jar)");
        assertThat(text).contains("RED    LoudPlugin 1.0.0  (loud.jar)");
        // Asserted without the column padding: the alignment is cosmetic and has churned twice.
        assertThat(squash(text))
                .contains(
                        "ERROR MISSING_METHOD"
                                + " org/bukkit/entity/Player#setDisplayName(Ljava/lang/String;)V");
        assertThat(text).contains("referenced from com/example/loud/Nick.rename()V");
        assertThat(squash(text))
                .contains("ERROR MISSING_FIELD org/bukkit/Material#LEGACY_STONE : Lorg/bukkit/Material;");
        assertThat(text).contains("2 plugins scanned: 1 GREEN, 0 YELLOW, 1 RED");
    }

    @Test
    void scansAFolderInFileNameOrder() throws IOException {
        ScanReport report = scanFolderWithOneGreenAndOneRed();

        assertThat(report.plugins())
                .extracting(p -> p.jarFileName())
                .containsExactly("loud.jar", "quiet.jar");
        assertThat(report.count(Verdict.GREEN)).isEqualTo(1);
        assertThat(report.count(Verdict.RED)).isEqualTo(1);
    }

    /** Collapses runs of spaces so assertions do not depend on column widths. */
    private static String squash(String text) {
        return text.replaceAll("[ \\t]+", " ");
    }

    private ScanReport scanFolderWithOneGreenAndOneRed() throws IOException {
        Path apiDir = Files.createDirectories(tempDir.resolve("api"));
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(apiDir));

        FixtureJars.pluginJar(
                plugins,
                "quiet.jar",
                "QuietPlugin",
                Map.of(
                        "com/example/quiet/Plugin",
                        Bytecode.pluginClass(
                                "com/example/quiet/Plugin",
                                callInterface(
                                        FixtureJars.PLAYER, "sendMessage", "(Ljava/lang/String;)V"))));

        FixtureJars.pluginJar(
                plugins,
                "loud.jar",
                "LoudPlugin",
                Map.of(
                        "com/example/loud/Nick",
                        Bytecode.pluginClass(
                                "com/example/loud/Nick",
                                "rename",
                                "()V",
                                callInterface(
                                        FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"),
                                readStaticField(
                                        FixtureJars.MATERIAL, "LEGACY_STONE", "Lorg/bukkit/Material;"))));

        return new PluginsFolderScanner(index).scan(plugins);
    }
}
