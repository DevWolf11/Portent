package dev.plugindoctor.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A snapshot of the API surface of one server version, produced from a local API jar.
 *
 * @param formatVersion bumped whenever the on-disk shape changes
 * @param source the file name of the jar the index was built from, for report headers
 * @param types internal name to {@link TypeInfo}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiIndex(
        @JsonProperty("formatVersion") int formatVersion,
        @JsonProperty("source") String source,
        @JsonProperty("types") Map<String, TypeInfo> types) {

    public static final int CURRENT_FORMAT_VERSION = 1;

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
}
