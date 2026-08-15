package pl.corekit.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.commands.BackCommand;
import pl.corekit.command.commands.CoreCommand;
import pl.corekit.command.commands.GameModeCommands;
import pl.corekit.command.commands.HomeCommands;
import pl.corekit.command.commands.PlayerUtilityCommands;
import pl.corekit.command.commands.SpawnCommands;
import pl.corekit.command.commands.TeleportRequestCommands;
import pl.corekit.feature.feedback.FeedbackService;

import java.util.List;

/**
 * Central hook into Paper's {@code LifecycleEvents.COMMANDS} event. It owns no
 * command logic itself — it simply collects every {@link CoreKitCommand} and
 * registers them together when the registrar becomes available.
 */
public final class CommandRegistrar {

    private final CoreKitPlugin plugin;
    private final List<CoreKitCommand> commands;

    public CommandRegistrar(CoreKitPlugin plugin) {
        this.plugin = plugin;
        FeedbackService feedback = plugin.feedback();
        this.commands = List.of(
                new CoreCommand(plugin, feedback),
                new PlayerUtilityCommands(plugin, feedback, plugin.god()),
                new GameModeCommands(plugin, feedback),
                new HomeCommands(plugin, feedback, plugin.homes(), plugin.teleport(), plugin.homeGui()),
                new SpawnCommands(plugin, feedback, plugin.spawn(), plugin.teleport()),
                new TeleportRequestCommands(plugin, feedback, plugin.teleportRequests()),
                new BackCommand(plugin, feedback, plugin.back(), plugin.teleport())
        );
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (CoreKitCommand command : commands) {
                command.register(registrar);
            }
        });
    }
}
