package dev.portent.scan;

import java.util.Map;

/**
 * Pre-26.1 world directory names.
 *
 * <p>26.1 moved world storage to {@code world/dimensions/minecraft/<dim>/}, so code that builds
 * paths from these names reads the wrong directory. Matching is on the whole constant, never a
 * substring: {@code "DIM1"} inside a longer string is far more likely to be someone's config key
 * than a world path, and a false positive here costs more than the missed finding.
 */
public final class WorldPaths {

    private static final Map<String, String> LEGACY_NAMES =
            Map.of(
                    "world_nether", "the pre-26.1 nether directory",
                    "world_the_end", "the pre-26.1 end directory",
                    "DIM-1", "the pre-26.1 nether region directory",
                    "DIM1", "the pre-26.1 end region directory");

    private WorldPaths() {}

    /** A description of the legacy path this constant names, or null if it names none. */
    public static String describe(String constant) {
        return constant == null ? null : LEGACY_NAMES.get(constant);
    }
}
