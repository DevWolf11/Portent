package dev.portent.index;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletenessTest {

    @TempDir Path tempDir;

    @Test
    void countsTypesWhoseSupertypeTheIndexCannotSee() throws IOException {
        Path apiJar =
                FixtureJars.rawJar(
                        tempDir,
                        "api.jar",
                        Map.of(
                                "org/bukkit/Thing.class",
                                Bytecode.apiInterface(
                                        "org/bukkit/Thing", List.of("net/kyori/adventure/Audience")),
                                "org/bukkit/Other.class",
                                Bytecode.apiInterface("org/bukkit/Other", List.of())));

        Completeness completeness = Completeness.of(IndexBuilder.fromJar(apiJar));

        assertThat(completeness.totalTypes()).isEqualTo(2);
        assertThat(completeness.incompleteTypes()).isEqualTo(1);
        assertThat(completeness.missingSupertypes()).containsKey("net/kyori/adventure/Audience");
    }

    @Test
    void doesNotCountJdkSupertypesAsGaps() throws IOException {
        // These resolve at scan time from the running JVM, so they cost nothing.
        Completeness completeness = Completeness.of(IndexBuilder.fromJar(FixtureJars.apiJar(tempDir)));

        assertThat(completeness.incompleteTypes()).isZero();
        assertThat(completeness.incompletePercent()).isZero();
        assertThat(completeness.isConcerning()).isFalse();
    }

    @Test
    void flagsAnIndexThatWouldUnderReport() throws IOException {
        Path apiJar =
                FixtureJars.rawJar(
                        tempDir,
                        "thin.jar",
                        Map.of(
                                "org/bukkit/A.class",
                                Bytecode.apiInterface("org/bukkit/A", List.of("net/kyori/Audience")),
                                "org/bukkit/B.class",
                                Bytecode.apiInterface("org/bukkit/B", List.of("net/kyori/Audience"))));

        assertThat(Completeness.of(IndexBuilder.fromJar(apiJar)).isConcerning()).isTrue();
    }
}
