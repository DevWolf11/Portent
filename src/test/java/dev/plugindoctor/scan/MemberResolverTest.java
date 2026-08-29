package dev.plugindoctor.scan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.plugindoctor.fixtures.FixtureJars;
import dev.plugindoctor.index.ApiIndex;
import dev.plugindoctor.index.IndexBuilder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The hierarchy walk, tested on its own. Inherited members are the biggest false-positive risk in
 * the whole tool, so this is exercised directly rather than only through a scan.
 */
class MemberResolverTest {

    private static MemberResolver resolver;

    @BeforeAll
    static void buildIndex(@TempDir Path tempDir) throws IOException {
        ApiIndex index = IndexBuilder.fromJar(FixtureJars.apiJar(tempDir));
        resolver = new MemberResolver(index);
    }

    @Test
    void findsMethodDeclaredOnTheOwnerItself() {
        assertThat(resolver.resolveMethod(FixtureJars.PLAYER, "sendMessage", "(Ljava/lang/String;)V"))
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void findsMethodDeclaredOnASuperclass() {
        assertThat(resolver.resolveMethod(FixtureJars.JAVA_PLUGIN, "getServer", "()Lorg/bukkit/Server;"))
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void findsMethodDeclaredOnAnImplementedInterface() {
        assertThat(resolver.resolveMethod(FixtureJars.PLAYER, "getName", "()Ljava/lang/String;"))
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void findsMethodInheritedFromJavaLangObject() {
        assertThat(resolver.resolveMethod(FixtureJars.PLAYER, "toString", "()Ljava/lang/String;"))
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void findsMethodInheritedFromJavaLangEnum() {
        assertThat(resolver.resolveMethod(FixtureJars.DIFFICULTY, "name", "()Ljava/lang/String;"))
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void reportsAbsentWhenNothingInTheHierarchyDeclaresIt() {
        assertThat(
                        resolver.resolveMethod(
                                FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))
                .isEqualTo(Resolution.ABSENT);
    }

    @Test
    void reportsAbsentWhenTheDescriptorNoLongerMatches() {
        assertThat(resolver.resolveMethod(FixtureJars.PLAYER, "sendMessage", "(Ljava/lang/Object;)V"))
                .isEqualTo(Resolution.ABSENT);
    }

    @Test
    void staysSilentWhenTheOwnerIsNotInTheIndex() {
        assertThat(resolver.resolveMethod("org/bukkit/entity/Villager", "getProfession", "()I"))
                .isEqualTo(Resolution.UNKNOWN);
    }

    @Test
    void staysSilentWhenASupertypeIsNotInTheIndex() throws IOException {
        // Owner is known, but its superclass is not, so the member may well be declared up there.
        ApiIndex partial =
                new ApiIndex(
                        ApiIndex.CURRENT_FORMAT_VERSION,
                        "partial.jar",
                        java.util.Map.of(
                                "org/bukkit/Thing",
                                new dev.plugindoctor.index.TypeInfo(
                                        "org/bukkit/Unknown",
                                        java.util.List.of(),
                                        null,
                                        java.util.Set.of(),
                                        java.util.Set.of(),
                                        null)));
        MemberResolver partialResolver = new MemberResolver(partial);

        assertThat(partialResolver.resolveMethod("org/bukkit/Thing", "gone", "()V"))
                .isEqualTo(Resolution.UNKNOWN);
    }

    @Test
    void findsFieldDeclaredOnTheOwner() {
        assertThat(resolver.resolveField(FixtureJars.MATERIAL, "STONE", "Lorg/bukkit/Material;"))
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void reportsAbsentField() {
        assertThat(
                        resolver.resolveField(
                                FixtureJars.MATERIAL, "LEGACY_STONE", "Lorg/bukkit/Material;"))
                .isEqualTo(Resolution.ABSENT);
    }

    @Test
    void doesNotInheritConstructors() {
        // getServer() is inherited by JavaPlugin, but a constructor never is.
        assertThat(resolver.resolveMethod(FixtureJars.JAVA_PLUGIN, "<init>", "()V"))
                .isEqualTo(Resolution.ABSENT);
    }
}
