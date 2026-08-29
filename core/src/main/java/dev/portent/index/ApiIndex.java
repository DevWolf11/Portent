package dev.portent.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A snapshot of the API surface of one server version, produced from a local API jar.
 *
 * @param formatVersion bumped whenever the on-disk shape changes
 * @param source the file name of the jar the index was built from, for report headers
 * @param minecraftVersion the target server version, or null if it could not be established
 * @param javaVersion the Java release the target server runs on, or 0 if unknown
 * @param types internal name to {@link TypeInfo}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiIndex(
        @JsonProperty("formatVersion") int formatVersion,
        @JsonProperty("source") String source,
        @JsonProperty("minecraftVersion") String minecraftVersion,
        @JsonProperty("javaVersion") int javaVersion,
        @JsonProperty("types") Map<String, TypeInfo> types) {

    /** Bumped in milestone 2: the index now carries target metadata. */
    public static final int CURRENT_FORMAT_VERSION = 2;

    public ApiIndex {
        types = types == null ? Map.of() : Map.copyOf(types);
    }

    /** The type, or null if this index does not describe it. */
    public TypeInfo type(String internalName) {
        return types.get(internalName);
    }

    public int typeCount() {
        return types.size();
    }

    /** The parsed target version, or null when the index does not record one. */
    public McVersion target() {
        return McVersion.parse(minecraftVersion);
    }

    /**
     * Whether the target is known to be at or past a threshold. False when the version is unknown,
     * so an unlabelled index never triggers a version-dependent ERROR.
     */
    public boolean targetIsAtLeast(int... threshold) {
        McVersion target = target();
        return target != null && target.isAtLeast(threshold);
    }
}
