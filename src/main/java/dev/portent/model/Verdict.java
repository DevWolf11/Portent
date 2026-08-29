package dev.portent.model;

public enum Verdict {
    /** Nothing was found. */
    GREEN,
    /** Warnings only: it should load, but read them before upgrading. */
    YELLOW,
    /** At least one ERROR: this plugin will not work on the target version. */
    RED,
    /** The jar was not a plugin, so no verdict was reached. */
    SKIPPED
}
