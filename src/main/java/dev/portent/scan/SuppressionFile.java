package dev.portent.scan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Reads the suppressions file.
 *
 * <p>Shape:
 *
 * <pre>
 * suppressions:
 *   - plugin: ViaVersion
 *     finding: MISSING_METHOD
 *     symbol: org/bukkit/block/Block#getTypeId()I
 *     reason: version-gated; only loaded on 1.8/1.9 servers
 * </pre>
 */
public final class SuppressionFile {

    private SuppressionFile() {}

    public static List<Suppression> read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    static List<Suppression> parse(InputStream in) throws IOException {
        LoaderOptions options = new LoaderOptions();
        DumperOptions dumper = new DumperOptions();
        Yaml yaml =
                new Yaml(
                        new SafeConstructor(options),
                        new Representer(dumper),
                        dumper,
                        options,
                        new StringOnlyResolver());

        Object loaded;
        try {
            loaded = yaml.load(in);
        } catch (RuntimeException e) {
            throw new IOException("could not parse suppressions: " + e.getMessage());
        }
        if (loaded == null) {
            return List.of();
        }
        if (!(loaded instanceof Map<?, ?> root) || !(root.get("suppressions") instanceof List<?> rows)) {
            throw new IOException("suppressions file must have a top-level `suppressions:` list");
        }

        List<Suppression> suppressions = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> entry)) {
                throw new IOException("each suppression must be a mapping, got: " + row);
            }
            try {
                suppressions.add(
                        new Suppression(
                                text(entry.get("plugin")),
                                text(entry.get("finding")),
                                text(entry.get("symbol")),
                                text(entry.get("reason"))));
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }
        return List.copyOf(suppressions);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /** Keeps every scalar a string, as in plugin descriptors. */
    private static final class StringOnlyResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            // Deliberately none.
        }
    }
}
