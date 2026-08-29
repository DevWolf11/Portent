package dev.portent.model;

/** How badly a finding hurts. */
public enum Severity {
    /** The plugin will not work on the target version. */
    ERROR,
    /** The plugin will probably load, but something here deserves attention before upgrading. */
    WARN
}
