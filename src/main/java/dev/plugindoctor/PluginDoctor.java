package dev.plugindoctor;

import dev.plugindoctor.cli.IndexCommand;
import dev.plugindoctor.cli.ScanCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "plugin-doctor",
        mixinStandardHelpOptions = true,
        version = "plugin-doctor 0.1.0",
        description = "Predict which Bukkit/Paper plugins break on a target Minecraft version.",
        subcommands = {IndexCommand.class, ScanCommand.class})
public final class PluginDoctor implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new PluginDoctor()).execute(args));
    }
}
