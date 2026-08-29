package dev.portent.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portent.scan.ClassScan;
import dev.portent.scan.ClassScanner;
import dev.portent.scan.SymbolReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Portent checked against Portent.
 *
 * <p>The plugin has to run on the server an admin is trying to leave, which is usually the oldest
 * one still in service. Compiling against a current paper-api makes it easy to reach for something
 * new by accident and only find out when someone's 1.13 server refuses to load the plugin. So the
 * Bukkit surface is pinned here, using the same scanner the tool points at everyone else's jars.
 */
class PluginApiSurfaceTest {

    /**
     * Every Bukkit member the plugin is allowed to touch. All of these have existed since the
     * early Bukkit days. Adding to this list is a deliberate act: check the member exists in the
     * oldest server you intend to support before you do.
     */
    private static final Set<String> ALLOWED =
            Set.of(
                    "org/bukkit/Server#getScheduler",
                    "org/bukkit/command/CommandSender#sendMessage",
                    "org/bukkit/command/PluginCommand#setExecutor",
                    "org/bukkit/plugin/java/JavaPlugin#<init>",
                    "org/bukkit/scheduler/BukkitScheduler#runTask",
                    "org/bukkit/scheduler/BukkitScheduler#runTaskAsynchronously");

    @Test
    void usesOnlyLongStableBukkitApi() throws IOException {
        Set<String> used = new TreeSet<>();
        for (Path classFile : pluginClassFiles()) {
            ClassScan scan =
                    ClassScanner.scan(Files.readAllBytes(classFile), null, owner -> true);
            for (SymbolReference reference : scan.references()) {
                used.add(reference.owner() + "#" + reference.name());
            }
        }

        assertThat(used)
                .as("the plugin reached for Bukkit API outside its pinned surface")
                .isSubsetOf(ALLOWED);
        assertThat(used).isNotEmpty();
    }

    @Test
    void compilesToJava17Bytecode() throws IOException {
        // Class file major 61 is Java 17. A newer target would not load on a 1.20.4 server, which
        // is exactly the server with the most breakage to report.
        for (Path classFile : pluginClassFiles()) {
            int major = ClassScanner.scan(Files.readAllBytes(classFile), null, o -> true).classFileMajor();
            assertThat(major).as(classFile.getFileName().toString()).isEqualTo(61);
        }
    }

    private static List<Path> pluginClassFiles() throws IOException {
        Path classes = Path.of("build/classes/java/main/dev/portent/plugin");
        assertThat(Files.isDirectory(classes))
                .as("plugin classes must be compiled before this test runs")
                .isTrue();
        try (Stream<Path> files = Files.walk(classes)) {
            return files.filter(p -> p.toString().endsWith(".class")).toList();
        }
    }
}
