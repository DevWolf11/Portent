package dev.portent.scan;

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

/** NMS and CraftBukkit internals detection, including how severity follows the target version. */
class InternalsDetectionTest {

    @TempDir Path tempDir;

    private static final String VERSIONED_CB = "org/bukkit/craftbukkit/v1_20_R3/CraftServer";
    private static final String LEGACY_NMS = "net/minecraft/server/v1_8_R3/MinecraftServer";
    private static final String MODERN_NMS = "net/minecraft/world/entity/Entity";
    private static final String UNVERSIONED_CB = "org/bukkit/craftbukkit/CraftServer";

    @Test
    void versionStampedInternalsAreFatalOnAnUnmappedTarget() throws IOException {
        PluginReport report = scanAgainst("26.1", VERSIONED_CB);

        Finding finding = only(report, FindingType.LEGACY_NMS);
        assertThat(finding.severity()).isEqualTo(Severity.ERROR);
        assertThat(finding.subject()).isEqualTo(VERSIONED_CB);
        assertThat(finding.detail()).contains("does not exist");
        assertThat(report.verdict()).isEqualTo(Verdict.RED);
    }

    @Test
    void thePre117NmsLayoutIsAlsoFatalOnAnUnmappedTarget() throws IOException {
        PluginReport report = scanAgainst("26.1", LEGACY_NMS);

        assertThat(only(report, FindingType.LEGACY_NMS).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void versionStampedInternalsAreOnlyAWarningOnAnEarlierTarget() throws IOException {
        PluginReport report = scanAgainst("1.21.4", VERSIONED_CB);

        assertThat(only(report, FindingType.LEGACY_NMS).severity()).isEqualTo(Severity.WARN);
        assertThat(report.verdict()).isEqualTo(Verdict.YELLOW);
    }

    @Test
    void anUnknownTargetVersionNeverEscalatesToAnError() throws IOException {
        // Prefer false negatives: without a target version we cannot know, so we do not claim.
        PluginReport report = scanAgainst(null, VERSIONED_CB);

        Finding finding = only(report, FindingType.LEGACY_NMS);
        assertThat(finding.severity()).isEqualTo(Severity.WARN);
        assertThat(finding.detail()).contains("no target version");
        assertThat(report.verdict()).isEqualTo(Verdict.YELLOW);
    }

    @Test
    void unversionedInternalsAreAWarningEvenOnAnUnmappedTarget() throws IOException {
        // 26.1 ships unobfuscated, so these packages do exist. They are unstable, not broken.
        for (String type : new String[] {MODERN_NMS, UNVERSIONED_CB}) {
            PluginReport report = scanAgainst("26.1", type);

            Finding finding = only(report, FindingType.SERVER_INTERNALS);
            assertThat(finding.severity()).isEqualTo(Severity.WARN);
            assertThat(finding.subject()).isEqualTo(type);
            assertThat(report.verdict()).isEqualTo(Verdict.YELLOW);
        }
    }

    @Test
    void classLevelEvidenceNamesTheClassWithoutADanglingMethod() throws IOException {
        PluginReport report = scanAgainst("26.1", VERSIONED_CB);

        assertThat(only(report, FindingType.LEGACY_NMS).callSites().get(0).toString())
                .isEqualTo("com/example/nms/Hook");
    }

    @Test
    void internalsAreNeverReportedAsMissingMembers() throws IOException {
        // We hold no index of internals, so claiming a member of one is gone would be a guess.
        PluginReport report = scanAgainst("26.1", VERSIONED_CB);

        assertThat(report.findings())
                .noneMatch(
                        f ->
                                f.type() == FindingType.MISSING_METHOD
                                        || f.type() == FindingType.MISSING_FIELD);
    }

    private PluginReport scanAgainst(String minecraftVersion, String internalsType)
            throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), minecraftVersion, 21);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Nms").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "com/example/nms/Hook.class",
                Bytecode.pluginClass(
                        "com/example/nms/Hook", callVirtual(internalsType, "getHandle", "()V")));
        Path jar =
                FixtureJars.rawJar(
                        tempDir, "nms-" + Math.abs(internalsType.hashCode()) + ".jar", entries);

        return new JarScanner(index).scan(jar);
    }

    private static Finding only(PluginReport report, FindingType type) {
        assertThat(report.findings()).filteredOn(f -> f.type() == type).hasSize(1);
        return report.findings().stream().filter(f -> f.type() == type).findFirst().orElseThrow();
    }
}
