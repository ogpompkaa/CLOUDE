package pl.corekit.command;

import io.papermc.paper.command.brigadier.Commands;

/**
 * A self-contained command (or group of related commands) that knows how to
 * register itself against Paper's Brigadier registrar. Grouping registration
 * per feature keeps {@link CommandRegistrar} a thin coordinator instead of one
 * ever-growing method.
 */
public interface CoreKitCommand {

    void register(Commands registrar);
}
