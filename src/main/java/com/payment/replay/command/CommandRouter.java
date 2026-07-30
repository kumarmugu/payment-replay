package com.payment.replay.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes CLI arguments to the appropriate Command implementation.
 * Acts as the central dispatcher for the application's command-line interface.
 *
 * Usage:
 *   java -jar payment-replay-tool.jar <command> [args...]
 */
public final class CommandRouter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /**
     * Registers a command with the router.
     *
     * @param command the command to register
     */
    public void register(Command command) {
        commands.put(command.getName(), command);
        log.debug("Registered command: {}", command.getName());
    }

    /**
     * Routes the CLI arguments to the appropriate command.
     *
     * @param args full command-line arguments
     * @return exit code from the executed command
     */
    public int route(String[] args) {
        if (args == null || args.length == 0) {
            printUsage();
            return 1;
        }

        String commandName = args[0].toLowerCase();

        if ("--help".equals(commandName) || "-h".equals(commandName)) {
            printUsage();
            return 0;
        }

        Command command = commands.get(commandName);
        if (command == null) {
            System.err.println("Unknown command: " + commandName);
            System.err.println();
            printUsage();
            return 1;
        }

        // Pass remaining args to the command
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        log.info("Executing command: {} with {} argument(s)", commandName, commandArgs.length);
        return command.execute(commandArgs);
    }

    /**
     * Prints usage information listing all available commands.
     */
    private void printUsage() {
        System.out.println("Payment Replay Tool v1.0.0");
        System.out.println();
        System.out.println("Usage: java -jar payment-replay-tool.jar <command> [args...]");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println();

        for (Command command : commands.values()) {
            System.out.printf("  %-16s %s%n", command.getName(), command.getDescription());
        }

        System.out.println();
        System.out.println("Use '<command> --help' for more information about a specific command.");
    }
}
