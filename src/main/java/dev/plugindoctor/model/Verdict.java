package dev.plugindoctor.model;

public enum Verdict {
    /** Nothing broken was found. */
    GREEN,
    /** At least one ERROR finding: this plugin will not work on the target version. */
    RED,
    /** The jar was not a plugin, so no verdict was reached. */
    SKIPPED
}
