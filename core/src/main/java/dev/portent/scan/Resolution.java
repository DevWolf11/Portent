package dev.portent.scan;

/** What the hierarchy walk was able to conclude about one referenced member. */
public enum Resolution {
    /** Declared on the owner or somewhere up its hierarchy. Safe. */
    PRESENT,
    /** Definitely not anywhere in a hierarchy we could see in full. This is a finding. */
    ABSENT,
    /**
     * Some type in the hierarchy is unknown, so the member may well exist. Never a finding: we
     * prefer a false negative to crying wolf.
     */
    UNKNOWN
}
