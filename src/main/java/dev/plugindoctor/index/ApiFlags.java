package dev.plugindoctor.index;

/**
 * Deprecation / stability markers, encoded as a short flag string so the index stays compact.
 *
 * <p>Milestone 1 does not report on these, but the index records them so that later milestones do
 * not require regenerating every index.
 */
public final class ApiFlags {

    /** Plain {@code @Deprecated}. */
    public static final char DEPRECATED = 'D';
    /** {@code @Deprecated(forRemoval = true)}. */
    public static final char FOR_REMOVAL = 'R';
    /** {@code @ApiStatus.Internal}. */
    public static final char INTERNAL = 'I';
    /** {@code @ApiStatus.Experimental}. */
    public static final char EXPERIMENTAL = 'X';

    private ApiFlags() {}

    public static boolean has(String flags, char flag) {
        return flags != null && flags.indexOf(flag) >= 0;
    }
}
