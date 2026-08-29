package dev.portent.cli;

import static dev.portent.fixtures.Bytecode.Reference.callInterface;
import static dev.portent.fixtures.Bytecode.Reference.callVirtual;
import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.Portent;
import dev.portent.fixtures.Bytecode;
import dev.portent.fixtures.FixtureJars;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** End-to-end through the CLI, including the exit codes a CI job would gate on. */
class ExitCodeTest {

    @TempDir Path tempDir;

    @Test
    void buildsAnIndexThenScansCleanAndExitsZero() throws IOException {
        Path index = buildIndex();
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        FixtureJars.pluginJar(
                plugins,
                "clean.jar",
                "Clean",
                Map.of(
                        "com/example/ok/Plugin",
                        Bytecode.pluginClass(
                                "com/example/ok/Plugin",
                                callInterface(
                                        FixtureJars.PLAYER, "sendMessage", "(Ljava/lang/String;)V"))));

        Run run = run("scan", "--plugins", plugins.toString(), "--index", index.toString());

        assertThat(run.exitCode()).isEqualTo(ExitCode.OK);
        assertThat(run.out()).contains("GREEN");
    }

    @Test
    void exitsOneWhenAPluginWillNotWork() throws IOException {
        Path index = buildIndex();
        Path plugins = Files.createDirectories(tempDir.resolve("plugins-red"));
        FixtureJars.pluginJar(
                plugins,
                "broken.jar",
                "Broken",
                Map.of(
                        "com/example/bad/Plugin",
                        Bytecode.pluginClass(
                                "com/example/bad/Plugin",
                                callInterface(
                                        FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))));

        Run run = run("scan", "--plugins", plugins.toString(), "--index", index.toString());

        assertThat(run.exitCode()).isEqualTo(ExitCode.FINDINGS);
        assertThat(run.out()).contains("RED");
    }

    @Test
    void warningsAloneDoNotFailTheBuild() throws IOException {
        // A YELLOW plugin still loads. Failing CI on it would train people to ignore the tool.
        Path index = buildIndex();
        Path plugins = Files.createDirectories(tempDir.resolve("plugins-yellow"));
        FixtureJars.pluginJar(
                plugins,
                "deprecated.jar",
                "Deprecated",
                Map.of(
                        "com/example/warn/Plugin",
                        Bytecode.pluginClass(
                                "com/example/warn/Plugin",
                                callVirtual(FixtureJars.SERVER, "reload", "()V"))));

        Run run = run("scan", "--plugins", plugins.toString(), "--index", index.toString());

        assertThat(run.exitCode()).isEqualTo(ExitCode.OK);
        assertThat(run.out()).contains("YELLOW");
    }

    @Test
    void exitsTwoOnAMissingIndex() throws IOException {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins-none"));

        Run run =
                run(
                        "scan",
                        "--plugins",
                        plugins.toString(),
                        "--index",
                        tempDir.resolve("nope.json").toString());

        assertThat(run.exitCode()).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void jsonFormatIsValidJsonAndStillCarriesTheExitCode() throws IOException {
        Path index = buildIndex();
        Path plugins = Files.createDirectories(tempDir.resolve("plugins-json"));
        FixtureJars.pluginJar(
                plugins,
                "broken.jar",
                "Broken",
                Map.of(
                        "com/example/j/Plugin",
                        Bytecode.pluginClass(
                                "com/example/j/Plugin",
                                callInterface(
                                        FixtureJars.PLAYER, "setDisplayName", "(Ljava/lang/String;)V"))));

        Run run =
                run(
                        "scan",
                        "--plugins",
                        plugins.toString(),
                        "--index",
                        index.toString(),
                        "--format",
                        "json");

        assertThat(run.exitCode()).isEqualTo(ExitCode.FINDINGS);
        assertThat(run.out().trim()).startsWith("{").endsWith("}");
        assertThat(run.out()).contains("\"MISSING_METHOD\"");
    }

    private Path buildIndex() throws IOException {
        Path apiJar = FixtureJars.apiJar(Files.createDirectories(tempDir.resolve("api")));
        Path index = tempDir.resolve("index.json");
        Run run =
                run(
                        "index",
                        "--api-jar",
                        apiJar.toString(),
                        "--out",
                        index.toString(),
                        "--minecraft-version",
                        "26.1");
        assertThat(run.exitCode()).isEqualTo(ExitCode.OK);
        assertThat(run.out()).contains("Target: 26.1");
        return index;
    }

    private record Run(int exitCode, String out) {}

    private static Run run(String... args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            int code = new CommandLine(new Portent()).execute(args);
            return new Run(code, captured.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }
}
