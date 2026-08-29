package dev.portent.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactFetcherTest {

    @TempDir Path tempDir;

    private static final MavenCoordinate LIB =
            new MavenCoordinate("com.example", "lib", "1.0.0");

    @Test
    void fetchesAndCaches() throws IOException {
        LocalRepository repo = repo().publish(LIB, "jar", "jar bytes".getBytes(StandardCharsets.UTF_8));
        AtomicInteger requests = new AtomicInteger();
        ArtifactFetcher fetcher = fetcher(repo, requests, false);

        Path first = fetcher.fetch(LIB, "jar");
        assertThat(first).isNotNull();
        assertThat(Files.readString(first)).isEqualTo("jar bytes");

        int afterFirst = requests.get();
        Path second = fetcher.fetch(LIB, "jar");

        assertThat(second).isEqualTo(first);
        assertThat(requests.get())
                .as("a cached artifact must not be requested again")
                .isEqualTo(afterFirst);
    }

    @Test
    void rejectsAnArtifactWhoseChecksumDoesNotMatch() throws IOException {
        // A downloaded jar is untrusted input.
        LocalRepository repo = repo().publishCorrupt(LIB, "tampered".getBytes(StandardCharsets.UTF_8));
        ArtifactFetcher fetcher = fetcher(repo, new AtomicInteger(), false);

        assertThatThrownBy(() -> fetcher.fetch(LIB, "jar"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    void doesNotCacheAnArtifactThatFailedVerification() throws IOException {
        LocalRepository repo = repo().publishCorrupt(LIB, "tampered".getBytes(StandardCharsets.UTF_8));
        ArtifactCache cache = new ArtifactCache(tempDir.resolve("cache"));
        ArtifactFetcher fetcher =
                new ArtifactFetcher(
                        List.of(repo.asRepository()), cache, new HttpTransport(), false);

        assertThatThrownBy(() -> fetcher.fetch(LIB, "jar")).isInstanceOf(IOException.class);

        // Otherwise the next run would silently reuse it.
        assertThat(cache.has(LIB, LIB.fileName("jar"))).isFalse();
    }

    @Test
    void returnsNullWhenNoRepositoryHasIt() throws IOException {
        assertThat(fetcher(repo(), new AtomicInteger(), false).fetch(LIB, "jar")).isNull();
    }

    @Test
    void offlineUsesTheCacheAndNeverAsks() throws IOException {
        LocalRepository repo = repo().publish(LIB, "jar", "cached".getBytes(StandardCharsets.UTF_8));
        AtomicInteger requests = new AtomicInteger();
        fetcher(repo, requests, false).fetch(LIB, "jar");
        int afterWarmUp = requests.get();

        ArtifactFetcher offline = fetcher(repo, requests, true);
        assertThat(offline.fetch(LIB, "jar")).isNotNull();
        assertThat(requests.get()).isEqualTo(afterWarmUp);

        assertThat(offline.fetch(new MavenCoordinate("com.example", "absent", "1.0"), "jar")).isNull();
    }

    @Test
    void resolvesTheCurrentBuildOfASnapshot() throws IOException {
        MavenCoordinate snapshot =
                new MavenCoordinate("com.example", "lib", "1.16.5-R0.1-SNAPSHOT");
        LocalRepository repo =
                repo()
                        .publishSnapshotMetadata(snapshot, "20211218.082619", "371")
                        .publishSnapshotJar(
                                snapshot,
                                "lib-1.16.5-R0.1-20211218.082619-371.jar",
                                "snapshot bytes".getBytes(StandardCharsets.UTF_8));

        Path jar = fetcher(repo, new AtomicInteger(), false).fetch(snapshot, "jar");

        assertThat(jar).isNotNull();
        assertThat(Files.readString(jar)).isEqualTo("snapshot bytes");
    }

    private LocalRepository repo() throws IOException {
        return new LocalRepository(tempDir.resolve("repo"));
    }

    /** Counts requests so caching and offline behaviour can be asserted, not assumed. */
    private ArtifactFetcher fetcher(LocalRepository repo, AtomicInteger requests, boolean offline) {
        Transport counting =
                url -> {
                    requests.incrementAndGet();
                    return new HttpTransport().get(url);
                };
        return new ArtifactFetcher(
                List.of(repo.asRepository()),
                new ArtifactCache(tempDir.resolve("cache")),
                counting,
                offline);
    }
}
