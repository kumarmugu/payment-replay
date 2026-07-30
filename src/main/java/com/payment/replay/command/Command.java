package com.payment.replay.command;

/**
 * Command interface for the CLI tool.
 * Each supported command (filter-mask, replay) implements this interface.
 *
 * Commands are registered with the CommandRouter and dispatched based on CLI arguments.
 */
public interface Command {

    /**
     * Executes the command with the provided arguments.
     *
     * @param args command-specific arguments (excludes the command name itself)
     * @return exit code (0 = success, non-zero = failure)
     */
    int execute(String[] args);

    /**
     * Returns the command name as used on the command line.
     * Example: "filter-mask", "replay"
     */
    String getName();

    /**
     * Returns a brief description of what the command does.
     */
    String getDescription();

    /**
     * Returns usage information for the command.
     */
    String getUsage();
}
