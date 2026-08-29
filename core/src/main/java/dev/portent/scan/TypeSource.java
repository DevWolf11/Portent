package dev.portent.scan;

import dev.portent.index.TypeInfo;

/** Somewhere the resolver can look a type up. Returns null when the type is unknown to it. */
@FunctionalInterface
public interface TypeSource {

    TypeInfo find(String internalName);

    /** A source that knows nothing, for tests that want the index consulted alone. */
    static TypeSource empty() {
        return internalName -> null;
    }
}
