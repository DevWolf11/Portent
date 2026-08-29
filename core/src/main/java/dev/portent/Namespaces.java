package dev.portent;

/**
 * The internal-name prefixes that portent is willing to report on.
 *
 * <p>Anything outside these namespaces is a shaded dependency or an optional soft-dep that the
 * server will never provide, so a "missing" reference to it says nothing about upgrade
 * compatibility. Reporting on it would be a false positive.
 */
public final class Namespaces {

    private static final String[] REPORTABLE = {
        "org/bukkit/",
        "io/papermc/",
        "com/destroystokyo/",
        "org/spigotmc/",
        "net/md_5/bungee/",
    };

    /**
     * Server internals. These are deliberately <em>not</em> in {@link #REPORTABLE}: we never claim
     * a member of them is missing, because we hold no index of them and guessing would be exactly
     * the false positive this tool must not produce. They are matched by package name only, to say
     * "this plugin reaches into internals", which is a different and much safer claim.
     */
    private static final String[] INTERNALS = {"net/minecraft/", "org/bukkit/craftbukkit/"};

    /**
     * Prefixes we are allowed to resolve against the running JDK's own class files. Bukkit types
     * inherit from {@code java.lang.Object}, {@code java.lang.Enum}, {@code java.lang.Iterable}
     * and friends, so the hierarchy walk has to be able to see them.
     */
    private static final String[] PLATFORM = {"java/", "javax/", "jdk/"};

    private Namespaces() {}

    /** True if a finding about this owner is worth showing to an admin. */
    public static boolean isReportable(String internalName) {
        return hasPrefix(internalName, REPORTABLE);
    }

    /** True if this type is server internals rather than public API. */
    public static boolean isServerInternals(String internalName) {
        return hasPrefix(internalName, INTERNALS);
    }

    /**
     * True if the name carries a Minecraft version stamp, as in
     * {@code org/bukkit/craftbukkit/v1_20_R3/CraftPlayer} or the pre-1.17
     * {@code net/minecraft/server/v1_8_R3/Entity} layout. These packages no longer exist on 26.1+.
     */
    public static boolean isVersionStamped(String internalName) {
        if (internalName == null) {
            return false;
        }
        for (String segment : internalName.split("/")) {
            if (segment.length() >= 2 && segment.charAt(0) == 'v' && isDigit(segment.charAt(1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** True if this type may be looked up in the running JDK's module image. */
    public static boolean isPlatform(String internalName) {
        return hasPrefix(internalName, PLATFORM);
    }

    private static boolean hasPrefix(String internalName, String[] prefixes) {
        if (internalName == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
