package dev.portent.scan;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static dev.portent.fixtures.Bytecode.Reference.callVirtual;
import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.model.Finding;
import dev.portent.model.FindingType;
import dev.portent.model.PluginReport;
import dev.portent.model.Severity;
import dev.portent.model.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** World-path constants, class file versions, and deprecation warnings. */
class UpgradeHazardsTest {

    @TempDir Path tempDir;

    @Test
    void findsHardcodedLegacyWorldDirectories() throws IOException {
        PluginReport report =
                scan(
                        "worlds.jar",
                        21,
                        Map.of(
                                "com/example/w/Paths",
                                Bytecode.pluginClassWithConstants(
                                        "com/example/w/Paths", "world_nether", "DIM-1", "plugins")));

        assertThat(report.findings())
                .filteredOn(f -> f.type() == FindingType.LEGACY_WORLD_PATH)
                .extracting(Finding::subject)
                .containsExactlyInAnyOrder("\"world_nether\"", "\"DIM-1\"");
        assertThat(report.verdict()).isEqualTo(Verdict.YELLOW);
    }

    @Test
    void doesNotMatchLegacyWorldNamesInsideLongerStrings() throws IOException {
        // "DIM1" is a short token. Substring matching here would fire on unrelated config keys,
        // and a false positive costs more than the missed finding.
        PluginReport report =
                scan(
                        "substrings.jar",
                        21,
                        Map.of(
                                "com/example/w/Config",
                                Bytecode.pluginClassWithConstants(
                                        "com/example/w/Config",
                                        "settings.DIM1.enabled",
                                        "my_world_nether_portal",
                                        "worlds/world_nether/region")));

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void flagsClassFilesTheTargetJvmCannotLoad() throws IOException {
        // Class file major 69 is Java 25; the target here runs Java 21.
        PluginReport report =
                scan(
                        "toonew.jar",
                        21,
                        Map.of(
                                "com/example/n/Modern",
                                Bytecode.pluginClassTargeting("com/example/n/Modern", 69)));

        Finding finding =
                report.findings().stream()
                        .filter(f -> f.type() == FindingType.UNSUPPORTED_CLASS_VERSION)
                        .findFirst()
                        .orElseThrow();
        assertThat(finding.severity()).isEqualTo(Severity.ERROR);
        assertThat(finding.subject()).contains("69").contains("Java 25");
        assertThat(finding.detail()).contains("Java 21");
        assertThat(report.verdict()).isEqualTo(Verdict.RED);
    }

    @Test
    void acceptsClassFilesTheTargetJvmCanLoad() throws IOException {
        PluginReport report =
                scan(
                        "fine.jar",
                        25,
                        Map.of(
                                "com/example/n/Ok",
                                Bytecode.pluginClassTargeting("com/example/n/Ok", 65)));

        assertThat(report.findings()).isEmpty();
    }

    @Test
    void ignoresClassVersionsOfMultiReleaseEntries() throws IOException {
        // A META-INF/versions/25 class is only ever loaded by a JVM that already supports it, so
        // its version says nothing about whether the plugin runs on an older server.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("MultiRelease").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "com/example/mr/Base.class", Bytecode.pluginClassTargeting("com/example/mr/Base", 61));
        entries.put(
                "META-INF/versions/25/com/example/mr/Base.class",
                Bytecode.pluginClassTargeting("com/example/mr/Base", 69));
        Path jar = FixtureJars.rawJar(tempDir, "mr.jar", entries);

        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), "26.1", 21);
        PluginReport report = new JarScanner(index).scan(jar);

        assertThat(report.findings())
                .noneMatch(f -> f.type() == FindingType.UNSUPPORTED_CLASS_VERSION);
    }

    @Test
    void warnsOnDeprecatedAndInternalApiButStaysYellow() throws IOException {
        PluginReport report =
                scan(
                        "deprecated.jar",
                        21,
                        Map.of(
                                "com/example/d/Legacy",
                                Bytecode.pluginClass(
                                        "com/example/d/Legacy",
                                        // kick is @Deprecated(forRemoval = true) in the fixture API
                                        callInterface(
                                                FixtureJars.PLAYER, "kick", "(Ljava/lang/String;)V"),
                                        // reload is plain @Deprecated
                                        callVirtual(FixtureJars.SERVER, "reload", "()V"),
                                        // getUnsafe is @ApiStatus.Internal
                                        callVirtual(
                                                FixtureJars.SERVER, "getUnsafe", "()Ljava/lang/Object;"))));

        assertThat(report.findings())
                .extracting(Finding::type)
                .containsExactlyInAnyOrder(
                        FindingType.DEPRECATED_FOR_REMOVAL,
                        FindingType.DEPRECATED_MEMBER,
                        FindingType.INTERNAL_API);
        assertThat(report.findings()).allMatch(f -> f.severity() == Severity.WARN);

        // A deprecated call still works today, so it must not read as "this will break".
        assertThat(report.verdict()).isEqualTo(Verdict.YELLOW);
    }

    @Test
    void aPresentUnflaggedCallStaysGreen() throws IOException {
        PluginReport report =
                scan(
                        "clean.jar",
                        21,
                        Map.of(
                                "com/example/c/Fine",
                                Bytecode.pluginClass(
                                        "com/example/c/Fine",
                                        callInterface(
                                                FixtureJars.PLAYER,
                                                "sendMessage",
                                                "(Ljava/lang/String;)V"))));

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    private PluginReport scan(String jarName, int targetJava, Map<String, byte[]> classes)
            throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), "26.1", targetJava);
        Path jar = FixtureJars.pluginJar(tempDir, jarName, "Hazards", classes);
        return new JarScanner(index).scan(jar);
    }
}
