package dev.plugindoctor;

/**
 * The internal-name prefixes that plugin-doctor is willing to report on.
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
