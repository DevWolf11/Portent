package dev.portent.scan;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.index.ApiIndex;
import dev.portent.index.IndexBuilder;
import dev.portent.model.CallSite;
import dev.portent.model.Finding;
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

/**
 * Regression tests for what real plugin jars actually look like, as opposed to what a tidy
 * fixture looks like.
 */
class NestedJarScanTest {

    @TempDir Path tempDir;

    private JarScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));
        scanner = new JarScanner(index);
    }

    @Test
    void findsBreakageInsideANestedJar() throws IOException {
        // LuckPerms ships 709 of its 905 classes this way. A scanner that reads only the outer jar
        // sees a loader stub, finds nothing, and reports GREEN.
        byte[] inner =
                FixtureJars.jarBytes(
                        Map.of(
                                "me/lucko/inner/Impl.class",
                                Bytecode.pluginClass(
                                        "me/lucko/inner/Impl",
                                        "apply",
                                        "()V",
                                        callInterface(
                                                FixtureJars.PLAYER,
                                                "setDisplayName",
                                                "(Ljava/lang/String;)V"))));

        Map<String, byte[]> outer = new LinkedHashMap<>();
        outer.put("plugin.yml", FixtureJars.pluginYml("Loaded").getBytes(StandardCharsets.UTF_8));
        outer.put(
                "me/lucko/loader/Loader.class",
                Bytecode.pluginClass("me/lucko/loader/Loader", "onEnable", "()V"));
        outer.put("plugin-impl.jarinjar", inner);
        Path jar = FixtureJars.rawJar(tempDir, "loaded.jar", outer);

        PluginReport report = scanner.scan(jar);

        assertThat(report.verdict()).isEqualTo(Verdict.RED);
        assertThat(report.classesScanned()).isEqualTo(2);
        assertThat(report.findings()).hasSize(1);

        Finding finding = report.findings().get(0);
        assertThat(finding.memberName()).isEqualTo("setDisplayName");

        // The evidence has to name the nested jar, or the admin cannot find the code.
        CallSite site = finding.callSites().get(0);
        assertThat(site.archive()).isEqualTo("plugin-impl.jarinjar");
        assertThat(site.callerClass()).isEqualTo("me/lucko/inner/Impl");
        assertThat(site.toString()).isEqualTo("plugin-impl.jarinjar!me/lucko/inner/Impl.apply()V");
    }

    @Test
    void stripsTheMultiReleasePrefixFromClassNames() {
        assertThat(ArchiveWalker.internalNameOf("META-INF/versions/9/com/foo/Bar.class"))
                .isEqualTo("com/foo/Bar");
        assertThat(ArchiveWalker.internalNameOf("META-INF/versions/21/com/foo/Bar.class"))
                .isEqualTo("com/foo/Bar");
        assertThat(ArchiveWalker.internalNameOf("com/foo/Bar.class")).isEqualTo("com/foo/Bar");
    }

    @Test
    void aMultiReleaseCopyOfABundledTypeIsStillRecognisedAsTheJarsOwn() throws IOException {
        // ViaVersion ships two classes under META-INF/versions/9. Without stripping the prefix they
        // are recorded under a name no reference can match, so the jar's own types look foreign.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("MultiRelease").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "META-INF/versions/9/org/bukkit/entity/Player.class",
                Bytecode.apiInterface(FixtureJars.PLAYER, java.util.List.of()));
        entries.put(
                "com/example/mr/Plugin.class",
                Bytecode.pluginClass(
                        "com/example/mr/Plugin",
                        callInterface(FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V")));
        Path jar = FixtureJars.rawJar(tempDir, "multi-release.jar", entries);

        PluginReport report = scanner.scan(jar);

        assertThat(report.findings()).isEmpty();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }

    @Test
    void findsBreakageReachedThroughAMethodReference() throws IOException {
        // ViaVersion reaches PlayerInventory.setItemInHand this way, and voicechat reaches
        // Command.testPermissionSilent. The target lives in an invokedynamic bootstrap argument,
        // not in a MethodInsnNode.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Lambdas").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "com/example/lambda/Plugin.class",
                Bytecode.pluginClassWithMethodReference(
                        "com/example/lambda/Plugin",
                        FixtureJars.PLAYER,
                        "setDisplayName",
                        "(Ljava/lang/String;)V"));
        Path jar = FixtureJars.rawJar(tempDir, "lambdas.jar", entries);

        PluginReport report = scanner.scan(jar);

        assertThat(report.verdict()).isEqualTo(Verdict.RED);
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).memberName()).isEqualTo("setDisplayName");
    }

    @Test
    void aMethodReferenceToAPresentMemberIsStillGreen() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Fine").getBytes(StandardCharsets.UTF_8));
        entries.put(
                "com/example/fine/Plugin.class",
                Bytecode.pluginClassWithMethodReference(
                        "com/example/fine/Plugin",
                        FixtureJars.PLAYER,
                        "sendMessage",
                        "(Ljava/lang/String;)V"));
        Path jar = FixtureJars.rawJar(tempDir, "fine.jar", entries);

        assertThat(scanner.scan(jar).findings()).isEmpty();
    }

    @Test
    void ignoresNestedJarsBeyondTheDepthCap() throws IOException {
        // Untrusted input: nesting must terminate.
        byte[] deepest =
                FixtureJars.jarBytes(
                        Map.of(
                                "com/deep/Deep.class",
                                Bytecode.pluginClass(
                                        "com/deep/Deep",
                                        callInterface(
                                                FixtureJars.PLAYER,
                                                "setDisplayName",
                                                "(Ljava/lang/String;)V"))));
        byte[] level3 = FixtureJars.jarBytes(Map.of("l4.jar", deepest));
        byte[] level2 = FixtureJars.jarBytes(Map.of("l3.jar", level3));
        byte[] level1 = FixtureJars.jarBytes(Map.of("l2.jar", level2));

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", FixtureJars.pluginYml("Deep").getBytes(StandardCharsets.UTF_8));
        entries.put("l1.jar", level1);
        Path jar = FixtureJars.rawJar(tempDir, "deep.jar", entries);

        PluginReport report = scanner.scan(jar);

        assertThat(report.classesScanned()).isZero();
        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
    }
}
