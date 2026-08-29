package dev.portent.scan;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.model.Finding;
import dev.portent.model.FindingType;
import dev.portent.model.PluginReport;
import dev.portent.model.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MissingClassTest {

    @TempDir Path tempDir;

    private JarScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), "26.1", 25);
        scanner = new JarScanner(index);
    }

    @Test
    void reportsATypeThatIsGoneFromACoveredPackage() throws IOException {
        // org/bukkit/entity is well represented in the index, so a type missing from it is gone.
        PluginReport report =
                scan("gone.jar", "org/bukkit/entity/Villager", "getProfession", "()I");

        Finding finding = only(report, FindingType.MISSING_CLASS);
        assertThat(finding.subject()).isEqualTo("org/bukkit/entity/Villager");
        assertThat(report.verdict()).isEqualTo(Verdict.RED);
    }

    @Test
    void reportsTheTypeOnceInsteadOfEachOfItsMembers() throws IOException {
        // Forty absent methods on one dead type is noise; the type is the actionable fact.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Gone").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "com/example/g/Uses.class",
                Bytecode.pluginClass(
                        "com/example/g/Uses",
                        callInterface("org/bukkit/entity/Villager", "getProfession", "()I"),
                        callInterface("org/bukkit/entity/Villager", "setProfession", "(I)V"),
                        callInterface("org/bukkit/entity/Villager", "isAdult", "()Z")));
        Path jar = FixtureJars.rawJar(tempDir, "many.jar", entries);

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).type()).isEqualTo(FindingType.MISSING_CLASS);
    }

    @Test
    void staysSilentAboutPackagesTheIndexDoesNotCover() throws IOException {
        // paper-api leaves Adventure and bungeecord-chat to Maven. An index without them knows
        // nothing about those packages, and guessing is how the tool loses trust.
        PluginReport report =
                scan("uncovered.jar", "net/md_5/bungee/api/chat/TextComponent", "getText", "()V");

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void staysSilentAboutTypesTheJarShipsItself() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Self").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "org/bukkit/entity/Villager.class",
                Bytecode.apiInterface("org/bukkit/entity/Villager", java.util.List.of()));
        entries.put(
                "com/example/s/Uses.class",
                Bytecode.pluginClass(
                        "com/example/s/Uses",
                        callInterface("org/bukkit/entity/Villager", "getProfession", "()I")));
        Path jar = FixtureJars.rawJar(tempDir, "self.jar", entries);

        assertThat(scanner.scan(jar).findings()).isEmpty();
    }

    private PluginReport scan(String jarName, String owner, String method, String descriptor)
            throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Test").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "com/example/t/Uses.class",
                Bytecode.pluginClass("com/example/t/Uses", callInterface(owner, method, descriptor)));
        return scanner.scan(FixtureJars.rawJar(tempDir, jarName, entries));
    }

    private static Finding only(PluginReport report, FindingType type) {
        assertThat(report.findings()).filteredOn(f -> f.type() == type).hasSize(1);
        return report.findings().stream().filter(f -> f.type() == type).findFirst().orElseThrow();
    }
}
