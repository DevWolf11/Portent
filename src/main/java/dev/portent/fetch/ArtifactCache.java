package dev.portent.fetch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A local mirror of what has been downloaded, laid out like a Maven repository.
 *
 * <p>The point is that a second run needs no network at all: once an index has been built for a
 * version, rebuilding it works on a disconnected machine.
 */
public final class ArtifactCache {

    private final Path root;

    public ArtifactCache(Path root) {
        this.root = root;
    }

    /** Default location, overridable so tests and CI never touch a user's home directory. */
    public static Path defaultRoot() {
        String override = System.getenv("PORTENT_CACHE");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home", "."), ".portent", "cache");
    }

    public Path root() {
        return root;
    }

    public Path pathFor(MavenCoordinate coordinate, String fileName) {
        return root.resolve(coordinate.directoryPath()).resolve(fileName);
    }

    public boolean has(MavenCoordinate coordinate, String fileName) {
        Path path = pathFor(coordinate, fileName);
        return Files.isRegularFile(path) && path.toFile().length() > 0;
    }

    public Path store(MavenCoordinate coordinate, String fileName, byte[] bytes) throws IOException {
        Path path = pathFor(coordinate, fileName);
        Files.createDirectories(path.getParent());
        // Write beside the target and move, so an interrupted download cannot look complete.
        Path temp = Files.createTempFile(path.getParent(), fileName, ".part");
        Files.write(temp, bytes);
        Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return path;
    }
}
