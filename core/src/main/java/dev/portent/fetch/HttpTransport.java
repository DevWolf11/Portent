package dev.portent.fetch;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** The real network transport. The only place in Portent that opens a socket. */
public final class HttpTransport implements Transport {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** Refuse absurd downloads rather than filling the disk. */
    private static final long MAX_BYTES = 256L * 1024 * 1024;

    private final HttpClient client;

    public HttpTransport() {
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .proxy(ProxySelector.getDefault())
                        .build();
    }

    @Override
    public byte[] get(String url) throws IOException {
        URI uri = URI.create(url);
        if ("file".equals(uri.getScheme())) {
            Path path = Path.of(uri);
            return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        }
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(TIMEOUT)
                        .header("User-Agent", "portent")
                        .GET()
                        .build();
        try {
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return null;
            }
            if (status != 200) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            byte[] body = response.body();
            if (body.length > MAX_BYTES) {
                throw new IOException("refusing " + body.length + " bytes from " + url);
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted fetching " + url, e);
        } catch (IOException e) {
            // A blocked proxy, no DNS, or no route all surface as opaque low-level errors. Say
            // which host could not be reached, so the cause is obvious.
            throw new IOException(
                    "could not reach " + uri.getHost() + ": " + e.getMessage(), e);
        }
    }
}
