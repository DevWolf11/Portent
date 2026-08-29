package dev.portent.index;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.portent.fixtures.FixtureJars;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarCheckTest {

    @TempDir Path tempDir;

    @Test
    void acceptsARealJar() throws IOException {
        assertThatCode(() -> JarCheck.require(FixtureJars.apiJar(tempDir))).doesNotThrowAnyException();
    }

    @Test
    void namesThePomMistakeSpecifically() throws IOException {
        // The .pom sits next to the .jar in every Maven repository, so this is the likely slip.
        Path pom = tempDir.resolve("paper-api-1.16.5-R0.1-SNAPSHOT.pom");
        Files.writeString(pom, "<project><artifactId>paper-api</artifactId></project>");

        assertThatThrownBy(() -> JarCheck.require(pom))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Maven POM, not a jar")
                .hasMessageContaining("beside it");
    }

    @Test
    void rejectsAnyOtherNonJar() throws IOException {
        Path text = tempDir.resolve("notes.txt");
        Files.write(text, "just some text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> JarCheck.require(text))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a jar");
    }

    @Test
    void reportsAMissingFileClearly() {
        assertThatThrownBy(() -> JarCheck.require(tempDir.resolve("absent.jar")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no such file");
    }
}
