package dev.portent.model;

/** What kind of problem a finding describes, and how bad it is by default. */
public enum FindingType {

    /** A referenced Bukkit/Paper method is absent from the target index. */
    MISSING_METHOD(Severity.ERROR),

    /** A referenced Bukkit/Paper field is absent from the target index. */
    MISSING_FIELD(Severity.ERROR),

    /**
     * A referenced Bukkit/Paper type is absent from the target index, in a package the index
     * otherwise covers. Reported instead of the members of that type, not alongside them.
     */
    MISSING_CLASS(Severity.ERROR),

    /**
     * A reference to a version-stamped internals package — {@code org/bukkit/craftbukkit/v1_*} or
     * the pre-1.17 {@code net/minecraft/server/v1_*} layout. Fatal from 26.1, where Paper dropped
     * its remapper and the versioned packages are gone.
     */
    LEGACY_NMS(Severity.ERROR),

    /**
     * A reference to unversioned server internals ({@code net/minecraft/...} or unversioned
     * {@code org/bukkit/craftbukkit/...}). These exist on an unobfuscated 26.1 server, so this is
     * a warning about fragility rather than a prediction of breakage.
     */
    SERVER_INTERNALS(Severity.WARN),

    /** A class file targets a newer Java release than the target server's JVM provides. */
    UNSUPPORTED_CLASS_VERSION(Severity.ERROR),

    /** A hardcoded pre-26.1 world directory name. */
    LEGACY_WORLD_PATH(Severity.WARN),

    /** The referenced member is {@code @Deprecated}. */
    DEPRECATED_MEMBER(Severity.WARN),

    /** The referenced member is {@code @Deprecated(forRemoval = true)}. */
    DEPRECATED_FOR_REMOVAL(Severity.WARN),

    /** The referenced member is {@code @ApiStatus.Internal}. */
    INTERNAL_API(Severity.WARN),

    /** The referenced member is {@code @ApiStatus.Experimental}. */
    EXPERIMENTAL_API(Severity.WARN);

    private final Severity defaultSeverity;

    FindingType(Severity defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public Severity defaultSeverity() {
        return defaultSeverity;
    }
}
