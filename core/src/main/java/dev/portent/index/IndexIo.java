package dev.portent.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes the compact JSON index. */
public final class IndexIo {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IndexIo() {}

    public static void write(ApiIndex index, Path out) throws IOException {
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (var stream = Files.newOutputStream(out)) {
            MAPPER.writeValue(stream, index);
        }
    }

    public static ApiIndex read(Path in) throws IOException {
        ApiIndex index;
        try (var stream = Files.newInputStream(in)) {
            index = MAPPER.readValue(stream, ApiIndex.class);
        }
        if (index.formatVersion() != ApiIndex.CURRENT_FORMAT_VERSION) {
            throw new IOException(
                    "index "
                            + in
                            + " has format version "
                            + index.formatVersion()
                            + ", this build expects "
                            + ApiIndex.CURRENT_FORMAT_VERSION
                            + " — regenerate it with `portent index`");
        }
        return index;
    }
}
