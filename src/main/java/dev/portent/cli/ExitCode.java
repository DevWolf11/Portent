package dev.portent.cli;

/** Process exit codes, so a scan can gate a CI job or an upgrade script. */
public final class ExitCode {

    /** Nothing that blocks the upgrade. Warnings alone still exit 0. */
    public static final int OK = 0;

    /** At least one plugin will not work on the target version. */
    public static final int FINDINGS = 1;

    /** Bad arguments, a missing file, or an unreadable index. */
    public static final int USAGE = 2;

    private ExitCode() {}
}
