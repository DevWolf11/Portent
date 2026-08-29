package dev.portent.scan;

import dev.portent.model.Finding;

/**
 * One admin-authored decision to accept a finding.
 *
 * <p>A reason is mandatory. A suppression without one becomes folklore that nobody dares delete,
 * and the whole point of the file is that a later reader can re-evaluate the call.
 *
 * @param plugin plugin name or jar file name this applies to; null matches any
 * @param type finding type name this applies to; null matches any
 * @param symbol the finding subject this applies to; null matches any
 * @param reason why this is accepted; required
 */
public record Suppression(String plugin, String type, String symbol, String reason) {

    public Suppression {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "every suppression needs a reason: "
                            + (symbol != null ? symbol : type != null ? type : plugin));
        }
        if (plugin == null && type == null && symbol == null) {
            throw new IllegalArgumentException(
                    "a suppression must narrow by plugin, finding or symbol; one matching everything"
                            + " would silence the whole scan");
        }
    }

    /**
     * @param pluginName the descriptor name, or null
     * @param jarFileName the jar's file name
     */
    public boolean matches(String pluginName, String jarFileName, Finding finding) {
        return matchesPlugin(pluginName, jarFileName)
                && (type == null || type.equalsIgnoreCase(finding.type().name()))
                && (symbol == null || symbol.equals(finding.subject()));
    }

    private boolean matchesPlugin(String pluginName, String jarFileName) {
        return plugin == null
                || plugin.equalsIgnoreCase(pluginName)
                || plugin.equalsIgnoreCase(jarFileName);
    }
}
