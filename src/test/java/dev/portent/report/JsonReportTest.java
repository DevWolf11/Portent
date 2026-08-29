package dev.portent.report;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.model.ScanReport;
import dev.portent.scan.PluginsFolderScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReportTest {

    @TempDir Path tempDir;

    @Test
    void emitsMachineReadableFindings() throws IOException {
        Path apiDir = Files.createDirectories(tempDir.resolve("api"));
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(apiDir), "26.1", 25);

        FixtureJars.pluginJar(
                plugins,
                "broken.jar",
                "BrokenPlugin",
                Map.of(
                        "com/example/b/Nick",
                        Bytecode.pluginClass(
                                "com/example/b/Nick",
                                "rename",
                                "()V",
                                callInterface(
                                        FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))));

        ScanReport report = new PluginsFolderScanner(index).scan(plugins);
        JsonNode json = new ObjectMapper().readTree(JsonReport.render(report));

        assertThat(json.get("minecraftVersion").asText()).isEqualTo("26.1");
        assertThat(json.get("javaVersion").asInt()).isEqualTo(25);

        JsonNode plugin = json.get("plugins").get(0);
        assertThat(plugin.get("verdict").asText()).isEqualTo("RED");
        assertThat(plugin.get("jarFileName").asText()).isEqualTo("broken.jar");

        JsonNode finding = plugin.get("findings").get(0);
        assertThat(finding.get("type").asText()).isEqualTo("MISSING_METHOD");
        assertThat(finding.get("severity").asText()).isEqualTo("ERROR");
        assertThat(finding.get("owner").asText()).isEqualTo("org/bukkit/entity/Player");
        assertThat(finding.get("memberName").asText()).isEqualTo("setDisplayName");
        assertThat(finding.get("descriptor").asText()).isEqualTo("(Ljava/lang/String;)V");
        assertThat(finding.get("callSites").get(0).get("callerClass").asText())
                .isEqualTo("com/example/b/Nick");
    }
}
