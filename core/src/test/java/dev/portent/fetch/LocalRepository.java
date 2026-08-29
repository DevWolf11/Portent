package dev.portent.fetch;

import dev.portent.fetch.ArtifactFetcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A Maven repository written to disk, so the fetch pipeline can be exercised end to end without a
 * socket. The suite must pass offline.
 */
public final class LocalRepository {

    private final Path root;

    public LocalRepository(Path root) throws IOException {
        this.root = Files.createDirectories(root);
    }

    public MavenRepository asRepository() {
        return new MavenRepository("local", root.toUri().toString());
    }

    /** Publishes an artifact with the SHA-1 Maven would put beside it. */
    public LocalRepository publish(MavenCoordinate coordinate, String extension, byte[] bytes)
            throws IOException {
        Path dir = Files.createDirectories(root.resolve(coordinate.directoryPath()));
        Path file = dir.resolve(coordinate.fileName(extension));
        Files.write(file, bytes);
        Files.writeString(
                dir.resolve(coordinate.fileName(extension) + ".sha1"),
                ArtifactFetcher.sha1(bytes),
                StandardCharsets.UTF_8);
        return this;
    }

    public LocalRepository publishPom(MavenCoordinate coordinate, String xml) throws IOException {
        return publish(coordinate, "pom", xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Publishes a jar whose SHA-1 sidecar is deliberately wrong. */
    public LocalRepository publishCorrupt(MavenCoordinate coordinate, byte[] bytes)
            throws IOException {
        Path dir = Files.createDirectories(root.resolve(coordinate.directoryPath()));
        Files.write(dir.resolve(coordinate.fileName("jar")), bytes);
        Files.writeString(
                dir.resolve(coordinate.fileName("jar") + ".sha1"),
                "0000000000000000000000000000000000000000",
                StandardCharsets.UTF_8);
        return this;
    }

    public LocalRepository publishSnapshotMetadata(
            MavenCoordinate coordinate, String timestamp, String buildNumber) throws IOException {
        Path dir = Files.createDirectories(root.resolve(coordinate.directoryPath()));
        Files.writeString(
                dir.resolve("maven-metadata.xml"),
                """
                <metadata>
                  <versioning>
                    <snapshot>
                      <timestamp>%s</timestamp>
                      <buildNumber>%s</buildNumber>
                    </snapshot>
                  </versioning>
                </metadata>
                """
                        .formatted(timestamp, buildNumber));
        return this;
    }

    /** Publishes a timestamped SNAPSHOT jar under the name the metadata points at. */
    public LocalRepository publishSnapshotJar(
            MavenCoordinate coordinate, String fileName, byte[] bytes) throws IOException {
        Path dir = Files.createDirectories(root.resolve(coordinate.directoryPath()));
        Files.write(dir.resolve(fileName), bytes);
        Files.writeString(dir.resolve(fileName + ".sha1"), ArtifactFetcher.sha1(bytes));
        return this;
    }
}
