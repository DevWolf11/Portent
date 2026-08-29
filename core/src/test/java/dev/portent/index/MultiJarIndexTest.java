package dev.portent.index;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import dev.portent.scan.MemberResolver;
import dev.portent.scan.Resolution;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * paper-api declares Adventure as a Maven dependency instead of bundling it, so indexing the API
 * jar alone leaves 16.5% of its types with a supertype the walk cannot see. Those types then answer
 * UNKNOWN for members that are genuinely gone.
 */
class MultiJarIndexTest {

    @TempDir Path tempDir;

    private static final String LIB_INTERFACE = "net/kyori/adventure/audience/Audience";
    private static final String API_TYPE = "org/bukkit/entity/Speaker";

    @Test
    void anUnindexedSupertypeMakesRemovedMembersUnknown() throws IOException {
        ApiIndex apiOnly = IndexBuilder.fromJars(List.of(apiJar()), "26.1", 25);
        MemberResolver resolver = new MemberResolver(apiOnly);

        // Gone from the target, but the walk cannot say so: Audience is not in the index.
        assertThat(resolver.resolveMethod(API_TYPE, "removedMethod", "()V").resolution())
                .isEqualTo(Resolution.UNKNOWN);
    }

    @Test
    void addingTheSupportingJarTurnsThatIntoAnAnswer() throws IOException {
        ApiIndex complete = IndexBuilder.fromJars(List.of(apiJar(), libJar()), "26.1", 25);
        MemberResolver resolver = new MemberResolver(complete);

        assertThat(resolver.resolveMethod(API_TYPE, "removedMethod", "()V").resolution())
                .isEqualTo(Resolution.ABSENT);
        // And a member that really is inherited from the library still resolves.
        assertThat(resolver.resolveMethod(API_TYPE, "sendMessage", "()V").resolution())
                .isEqualTo(Resolution.PRESENT);
    }

    @Test
    void onlyTheFirstJarFixesTheTargetMetadata() throws IOException {
        // A supporting library built for an older Java must not drag the target release down.
        ApiIndex complete = IndexBuilder.fromJars(List.of(apiJar(), libJar()), null, 0);

        assertThat(complete.source()).isEqualTo("fixture-api.jar");
        assertThat(complete.javaVersion()).isEqualTo(21);
    }

    private Path apiJar() throws IOException {
        Path jar = FixtureJars.apiJar(tempDir);
        // Rebuild it with one extra type that inherits from a library interface.
        return FixtureJars.rawJar(
                tempDir,
                "fixture-api.jar",
                Map.of(
                        API_TYPE + ".class",
                        Bytecode.apiInterface(API_TYPE, List.of(LIB_INTERFACE)),
                        FixtureJars.PLAYER + ".class",
                        Bytecode.apiInterface(FixtureJars.PLAYER, List.of())));
    }

    private Path libJar() throws IOException {
        return FixtureJars.rawJar(
                tempDir,
                "adventure-api.jar",
                Map.of(
                        LIB_INTERFACE + ".class",
                        Bytecode.apiInterface(
                                LIB_INTERFACE, List.of(), Bytecode.Member.method("sendMessage", "()V"))));
    }
}
