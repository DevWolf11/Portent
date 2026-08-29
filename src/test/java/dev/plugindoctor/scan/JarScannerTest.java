package dev.plugindoctor.scan;

import static dev.plugindoctor.fixtures.Bytecode.Reference.callInterface;
import static dev.plugindoctor.fixtures.Bytecode.Reference.callVirtual;
import static dev.plugindoctor.fixtures.Bytecode.Reference.readStaticField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.plugindoctor.fixtures.Bytecode;
import dev.plugindoctor.fixtures.FixtureJars;
import dev.plugindoctor.index.ApiIndex;
import dev.plugindoctor.index.IndexBuilder;
import dev.plugindoctor.model.CallSite;
import dev.plugindoctor.model.Finding;
import dev.plugindoctor.model.FindingType;
import dev.plugindoctor.model.PluginReport;
import dev.plugindoctor.model.Severity;
import dev.plugindoctor.model.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarScannerTest {

    @TempDir Path tempDir;

    private JarScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));
        scanner = new JarScanner(new MemberResolver(index));
    }

    @Test
    void callingAPresentMethodIsGreenWithNoFindings() throws IOException {
        Path jar =
                pluginJar(
                        "good.jar",
                        "GoodPlugin",
                        Map.of(
                                "com/example/good/Plugin",
                                Bytecode.pluginClass(
                                        "com/example/good/Plugin",
                                        callInterface(
                                                FixtureJars.PLAYER,
                                                "sendMessage",
                                                "(Ljava/lang/String;)V"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
        assertThat(report.findings()).isEmpty();
        assertThat(report.classesScanned()).isEqualTo(1);
        assertThat(report.descriptor().name()).isEqualTo("GoodPlugin");
    }

    @Test
    void callingAnAbsentMethodIsRedWithFullEvidence() throws IOException {
        Path jar =
                pluginJar(
                        "broken.jar",
                        "BrokenPlugin",
                        Map.of(
                                "com/example/broken/Nick",
                                Bytecode.pluginClass(
                                        "com/example/broken/Nick",
                                        "applyNickname",
                                        "(Ljava/lang/String;)V",
                                        callInterface(
                                                FixtureJars.PLAYER,
                                                "setDisplayName",
                                                "(Ljava/lang/String;)V"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.verdict()).isEqualTo(Verdict.RED);
        assertThat(report.findings()).hasSize(1);

        Finding finding = report.findings().getFirst();
        assertThat(finding.type()).isEqualTo(FindingType.MISSING_METHOD);
        assertThat(finding.severity()).isEqualTo(Severity.ERROR);
        assertThat(finding.owner()).isEqualTo("org/bukkit/entity/Player");
        assertThat(finding.memberName()).isEqualTo("setDisplayName");
        assertThat(finding.descriptor()).isEqualTo("(Ljava/lang/String;)V");
        assertThat(finding.callSites())
                .containsExactly(
                        CallSite.of(
                                "com/example/broken/Nick", "applyNickname", "(Ljava/lang/String;)V"));
    }

    @Test
    void callingAnAbsentFieldIsRedWithAMissingFieldFinding() throws IOException {
        Path jar =
                pluginJar(
                        "legacy.jar",
                        "LegacyPlugin",
                        Map.of(
                                "com/example/legacy/Blocks",
                                Bytecode.pluginClass(
                                        "com/example/legacy/Blocks",
                                        readStaticField(
                                                FixtureJars.MATERIAL,
                                                "LEGACY_STONE",
                                                "Lorg/bukkit/Material;"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.verdict()).isEqualTo(Verdict.RED);
        assertThat(report.findings()).hasSize(1);
        Finding finding = report.findings().getFirst();
        assertThat(finding.type()).isEqualTo(FindingType.MISSING_FIELD);
        assertThat(finding.owner()).isEqualTo("org/bukkit/Material");
        assertThat(finding.memberName()).isEqualTo("LEGACY_STONE");
        assertThat(finding.descriptor()).isEqualTo("Lorg/bukkit/Material;");
        assertThat(finding.callSites()).hasSize(1);
    }

    @Test
    void callingAMethodDeclaredOnASuperclassOfTheOwnerIsGreen() throws IOException {
        // The reference names JavaPlugin, but getServer() is declared on PluginBase.
        Path jar =
                pluginJar(
                        "inherited.jar",
                        "InheritedPlugin",
                        Map.of(
                                "com/example/inherited/Plugin",
                                Bytecode.pluginClass(
                                        "com/example/inherited/Plugin",
                                        callVirtual(
                                                FixtureJars.JAVA_PLUGIN,
                                                "getServer",
                                                "()Lorg/bukkit/Server;"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void callingAMethodDeclaredOnAnInterfaceOfTheOwnerIsGreen() throws IOException {
        // The reference names Player, but getName() is declared on Entity, which Player extends.
        Path jar =
                pluginJar(
                        "iface.jar",
                        "InterfacePlugin",
                        Map.of(
                                "com/example/iface/Plugin",
                                Bytecode.pluginClass(
                                        "com/example/iface/Plugin",
                                        callInterface(
                                                FixtureJars.PLAYER, "getName", "()Ljava/lang/String;"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void missingCallsIntoShadedPackagesAreIgnored() throws IOException {
        Path jar =
                pluginJar(
                        "shaded.jar",
                        "ShadedPlugin",
                        Map.of(
                                "com/example/shaded/Plugin",
                                Bytecode.pluginClass(
                                        "com/example/shaded/Plugin",
                                        callVirtual(
                                                "com/google/common/collect/ImmutableList",
                                                "definitelyGone",
                                                "()V"),
                                        callVirtual("net/milkbowl/vault/economy/Economy", "gone", "()V"),
                                        callVirtual("org/slf4j/Logger", "gone", "()V"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void aJarWithoutADescriptorIsSkippedCleanly() throws IOException {
        Path jar =
                FixtureJars.jarWithoutDescriptor(
                        tempDir,
                        "library.jar",
                        Map.of(
                                "com/example/lib/Util",
                                Bytecode.pluginClass(
                                        "com/example/lib/Util",
                                        callInterface(
                                                FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))));

        PluginReport report = assertNoThrow(jar);

        assertThat(report.verdict()).isEqualTo(Verdict.SKIPPED);
        assertThat(report.findings()).isEmpty();
        assertThat(report.skipReason()).contains("plugin.yml").contains("paper-plugin.yml");
    }

    @Test
    void aCorruptClassIsReportedAndTheRestOfTheJarIsStillScanned() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("PartlyBroken").getBytes(StandardCharsets.UTF_8));
        entries.put("com/example/partly/Broken.class", FixtureJars.corruptClassBytes());
        entries.put(
                "com/example/partly/Fine.class",
                Bytecode.pluginClass(
                        "com/example/partly/Fine",
                        callInterface(FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V")));
        Path jar = FixtureJars.rawJar(tempDir, "partly-broken.jar", entries);

        PluginReport report = assertNoThrow(jar);

        assertThat(report.unreadableClasses()).hasSize(1);
        assertThat(report.unreadableClasses().getFirst().entryName())
                .isEqualTo("com/example/partly/Broken.class");
        assertThat(report.unreadableClasses().getFirst().reason()).isNotBlank();

        // The scan carried on: the good class next to it was still walked and still reported.
        assertThat(report.classesScanned()).isEqualTo(1);
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().memberName()).isEqualTo("setDisplayName");
        assertThat(report.verdict()).isEqualTo(Verdict.RED);
    }

    @Test
    void repeatedReferencesToOneSymbolCollapseIntoOneFinding() throws IOException {
        Path jar =
                pluginJar(
                        "repeat.jar",
                        "RepeatPlugin",
                        Map.of(
                                "com/example/repeat/A",
                                Bytecode.pluginClass(
                                        "com/example/repeat/A",
                                        "one",
                                        "()V",
                                        callInterface(
                                                FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"),
                                        callInterface(
                                                FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V")),
                                "com/example/repeat/B",
                                Bytecode.pluginClass(
                                        "com/example/repeat/B",
                                        "two",
                                        "()V",
                                        callInterface(
                                                FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))));

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().callSites())
                .extracting(CallSite::callerClass)
                .containsExactly("com/example/repeat/A", "com/example/repeat/B");
    }

    @Test
    void bukkitNamedClassesShippedInsideTheJarAreNotReported() throws IOException {
        // Some plugins bundle their own copy of an API type. It is present at runtime, so a
        // reference to it says nothing about the server version.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("SelfContained").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "org/bukkit/entity/Player.class",
                Bytecode.apiInterface(FixtureJars.PLAYER, java.util.List.of()));
        entries.put(
                "com/example/self/Plugin.class",
                Bytecode.pluginClass(
                        "com/example/self/Plugin",
                        callInterface(FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V")));
        Path jar = FixtureJars.rawJar(tempDir, "self-contained.jar", entries);

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    private Path pluginJar(String fileName, String name, Map<String, byte[]> classes)
            throws IOException {
        return FixtureJars.pluginJar(tempDir, fileName, name, classes);
    }

    private PluginReport assertNoThrow(Path jar) {
        assertThatCode(() -> scanner.scan(jar)).doesNotThrowAnyException();
        return scanner.scan(jar);
    }
}
