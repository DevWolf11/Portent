package dev.portent.plugin;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Portent as a server plugin.
 *
 * <p>The CLI answers the same question, but it asks an admin to find a terminal, install a JDK and
 * learn two commands before they learn anything about their server. Installed here, the answer is
 * one command in the console of the server they already run.
 *
 * <p>This deliberately uses a very small, very old slice of the Bukkit API. It has to run on the
 * server someone is trying to move away from -- often the oldest one still in service, which is
 * exactly where the breakage is worst.
 */
public final class PortentPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfigIfAbsent();
        getCommand("portent").setExecutor(new PortentCommand(this));
        getLogger().info("Portent ready. Run /portent check <version> to see what would break.");
    }

    /** The data folder doubles as the cache and report directory. */
    private void saveDefaultConfigIfAbsent() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning(
                    "Could not create " + getDataFolder() + "; reports will not be written to disk.");
        }
    }
}
