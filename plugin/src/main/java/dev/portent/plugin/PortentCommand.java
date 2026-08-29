package dev.portent.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Handles {@code /portent}. All real work happens off the main thread. */
public final class PortentCommand implements CommandExecutor, TabCompleter {

    private final PortentPlugin plugin;
    private final CheckRunner runner;

    public PortentCommand(PortentPlugin plugin) {
        this.plugin = plugin;
        this.runner = new CheckRunner(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Portent - checks your plugins against a Minecraft version.");
            sender.sendMessage("  /" + label + " check <version>   e.g. /" + label + " check 26.1.2");
            return true;
        }
        if (!"check".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Unknown subcommand. Try /" + label + " help");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Which version are you moving to? e.g. /" + label + " check 26.1.2");
            return true;
        }

        // Indexing and scanning read every class in every plugin jar. That is far too much work
        // for the main thread, which would freeze the server while it ran.
        runner.startAsync(sender, args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("check", "help"));
            options.removeIf(o -> !o.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return options;
        }
        return Collections.emptyList();
    }
}
