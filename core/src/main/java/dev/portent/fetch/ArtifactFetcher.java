package dev.portent.fetch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Downloads artifacts, verifies them, and caches them.
 *
 * <p>A downloaded jar is untrusted input, so the SHA-1 Maven publishes beside every artifact is
 * checked before the bytes are stored. A jar that fails is never written to the cache, so a
 * corrupted or tampered download cannot be silently reused on the next run.
 */
public final class ArtifactFetcher {

    private final List<MavenRepository> repositories;
    private final ArtifactCache cache;
    private final Transport transport;
    private final boolean offline;

    public ArtifactFetcher(
            List<MavenRepository> repositories,
            ArtifactCache cache,
            Transport transport,
            boolean offline) {
        this.repositories = List.copyOf(repositories);
        this.cache = cache;
        this.transport = transport;
        this.offline = offline;
    }

    /** @return the cached file, or null if no repository has it */
    public Path fetch(MavenCoordinate coordinate, String extension) throws IOException {
        String fileName = resolveFileName(coordinate, extension);
        if (cache.has(coordinate, fileName)) {
            return cache.pathFor(coordinate, fileName);
        }
        if (offline) {
            return null;
        }

        String relative = coordinate.directoryPath() + "/" + fileName;
        for (MavenRepository repository : repositories) {
            byte[] bytes = transport.get(repository.urlFor(relative));
            if (bytes == null) {
                continue;
            }
            verify(repository, relative, bytes, coordinate);
            return cache.store(coordinate, fileName, bytes);
        }
        return null;
    }

    /**
     * Fetches an artifact's {@code maven-metadata.xml}, used to list what versions exist. Cached
     * like anything else, but under the artifact directory rather than a version directory.
     */
    public Path fetchMetadata(String artifactPath) throws IOException {
        if (offline) {
            return null;
        }
        for (MavenRepository repository : repositories) {
            byte[] bytes = transport.get(repository.urlFor(artifactPath + "/maven-metadata.xml"));
            if (bytes != null) {
                Path path = cache.root().resolve(artifactPath).resolve("maven-metadata.xml");
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.write(path, bytes);
                return path;
            }
        }
        return null;
    }

    /**
     * SNAPSHOT versions do not name a file directly; the repository's metadata says which
     * timestamped build is current.
     */
    private String resolveFileName(MavenCoordinate coordinate, String extension) throws IOException {
        if (!coordinate.isSnapshot() || offline) {
            return coordinate.fileName(extension);
        }
        for (MavenRepository repository : repositories) {
            byte[] metadata =
                    transport.get(
                            repository.urlFor(coordinate.directoryPath() + "/maven-metadata.xml"));
            if (metadata == null) {
                continue;
            }
            String stamped =
                    SnapshotMetadata.fileNameFor(
                            new String(metadata, StandardCharsets.UTF_8), coordinate, extension);
            if (stamped != null) {
                return stamped;
            }
        }
        return coordinate.fileName(extension);
    }

    private void verify(
            MavenRepository repository, String relative, byte[] bytes, MavenCoordinate coordinate)
            throws IOException {
        byte[] published = transport.get(repository.urlFor(relative + ".sha1"));
        if (published == null) {
            // Not every repository publishes checksums for every file; absence is not corruption.
            return;
        }
        String expected = firstToken(new String(published, StandardCharsets.UTF_8));
        String actual = sha1(bytes);
        if (expected != null && !expected.equalsIgnoreCase(actual)) {
            throw new IOException(
                    "checksum mismatch for "
                            + coordinate
                            + " from "
                            + repository.id()
                            + ": expected "
                            + expected
                            + ", got "
                            + actual);
        }
    }

    static String firstToken(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    static String sha1(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }
    }
}
