package dev.portent.index;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.FixtureJars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexBuilderTest {

    @TempDir Path tempDir;

    @Test
    void recordsHierarchyAndMembers() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));

        TypeInfo player = index.type(FixtureJars.PLAYER);
        assertThat(player).isNotNull();
        assertThat(player.interfaces()).containsExactly(FixtureJars.ENTITY);
        assertThat(player.hasMethod("sendMessage(Ljava/lang/String;)V")).isTrue();
        assertThat(player.hasMethod("getName()Ljava/lang/String;")).isFalse(); // inherited, not declared

        TypeInfo javaPlugin = index.type(FixtureJars.JAVA_PLUGIN);
        assertThat(javaPlugin.superName()).isEqualTo(FixtureJars.PLUGIN_BASE);

        TypeInfo material = index.type(FixtureJars.MATERIAL);
        assertThat(material.hasField("STONE:Lorg/bukkit/Material;")).isTrue();
        assertThat(material.hasField("LEGACY_STONE:Lorg/bukkit/Material;")).isFalse();
    }

    @Test
    void normalisesJavaLangObjectSuperclassToNull() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));

        assertThat(index.type(FixtureJars.SERVER).superName()).isNull();
        assertThat(index.type(FixtureJars.DIFFICULTY).superName()).isEqualTo("java/lang/Enum");
    }

    @Test
    void distinguishesDeprecationKinds() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));

        String kick = index.type(FixtureJars.PLAYER).flagsOf("kick(Ljava/lang/String;)V");
        assertThat(ApiFlags.has(kick, ApiFlags.DEPRECATED)).isTrue();
        assertThat(ApiFlags.has(kick, ApiFlags.FOR_REMOVAL)).isTrue();

        TypeInfo server = index.type(FixtureJars.SERVER);
        String reload = server.flagsOf("reload()V");
        assertThat(ApiFlags.has(reload, ApiFlags.DEPRECATED)).isTrue();
        assertThat(ApiFlags.has(reload, ApiFlags.FOR_REMOVAL)).isFalse();

        assertThat(ApiFlags.has(server.flagsOf("getUnsafe()Ljava/lang/Object;"), ApiFlags.INTERNAL))
                .isTrue();

        // Members that carry nothing are not weighed down with an empty entry.
        assertThat(server.flagsOf("getVersion()Ljava/lang/String;")).isNull();
    }

    @Test
    void infersTheTargetJavaReleaseFromClassFileVersions() throws IOException {
        // The fixture API jar is written at class file major 65.
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));

        assertThat(index.javaVersion()).isEqualTo(21);
        assertThat(IndexBuilder.releaseOf(69)).isEqualTo(25);
    }

    @Test
    void anExplicitTargetOverridesWhatWasInferred() throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir), "26.1", 25);

        assertThat(index.minecraftVersion()).isEqualTo("26.1");
        assertThat(index.javaVersion()).isEqualTo(25);
        assertThat(index.targetIsAtLeast(26, 1)).isTrue();
    }

    @Test
    void anUnknownTargetNeverSatisfiesAThreshold() throws IOException {
        // This is what keeps an unlabelled index from producing version-dependent errors.
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));

        assertThat(index.minecraftVersion()).isNull();
        assertThat(index.targetIsAtLeast(26, 1)).isFalse();
    }

    @Test
    void roundTripsThroughJson() throws IOException {
        ApiIndex original = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));
        Path out = tempDir.resolve("nested/index.json");

        IndexIo.write(original, out);
        ApiIndex reloaded = IndexIo.read(out);

        assertThat(reloaded.formatVersion()).isEqualTo(ApiIndex.CURRENT_FORMAT_VERSION);
        assertThat(reloaded.source()).isEqualTo("fixture-api.jar");
        assertThat(reloaded.minecraftVersion()).isEqualTo(original.minecraftVersion());
        assertThat(reloaded.javaVersion()).isEqualTo(original.javaVersion());
        assertThat(reloaded.typeCount()).isEqualTo(original.typeCount());
        assertThat(reloaded.type(FixtureJars.PLAYER)).isEqualTo(original.type(FixtureJars.PLAYER));
        assertThat(reloaded.type(FixtureJars.JAVA_PLUGIN).superName())
                .isEqualTo(FixtureJars.PLUGIN_BASE);
    }

    @Test
    void isDeterministic() throws IOException {
        Path apiJar = FixtureJars.apiJar(tempDir);
        Path first = tempDir.resolve("first.json");
        Path second = tempDir.resolve("second.json");

        IndexIo.write(IndexBuilder.fromJar(apiJar), first);
        IndexIo.write(IndexBuilder.fromJar(apiJar), second);

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }
}
