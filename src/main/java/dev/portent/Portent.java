package dev.portent;

import dev.portent.cli.IndexCommand;
import dev.portent.cli.ScanCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "portent",
        mixinStandardHelpOptions = true,
        version = "portent 0.1.0",
        description = "Predict which Bukkit/Paper plugins break on a target Minecraft version.",
        subcommands = {IndexCommand.class, ScanCommand.class})
public final class Portent implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Portent()).execute(args));
    }
}
