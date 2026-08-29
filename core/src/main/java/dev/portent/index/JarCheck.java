package dev.portent.index;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Confirms a file is actually a jar before ASM is pointed at it.
 *
 * <p>The common mistake is handing over the {@code .pom} that sits beside the jar in a Maven
 * repository. That file describes dependencies and carries no bytecode, so the useful response is
 * to name the mistake rather than to surface a zip parsing stack trace.
 */
public final class JarCheck {

    private JarCheck() {}

    /** @throws IOException with an actionable message if this is not a readable jar */
    public static void require(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("no such file: " + file);
        }
        byte[] magic = new byte[4];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(magic, 0, 4);
        }
        if (read == 4 && magic[0] == 'P' && magic[1] == 'K') {
            return;
        }

        String name = file.getFileName().toString();
        if (name.endsWith(".pom") || (read > 0 && (magic[0] == '<' || isXmlPrologue(magic)))) {
            throw new IOException(
                    file
                            + " is a Maven POM, not a jar. A POM lists dependencies and contains no"
                            + " bytecode, so there is nothing to index. Use the .jar file that sits"
                            + " beside it in the same directory.");
        }
        throw new IOException(file + " is not a jar (no zip header). Point --api-jar at the API jar.");
    }

    private static boolean isXmlPrologue(byte[] magic) {
        return magic[0] == (byte) 0xEF && magic[1] == (byte) 0xBB && magic[2] == (byte) 0xBF;
    }
}
