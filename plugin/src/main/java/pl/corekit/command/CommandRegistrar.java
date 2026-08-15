package pl.corekit.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.commands.CoreCommand;
import pl.corekit.command.commands.GameModeCommands;
import pl.corekit.command.commands.HomeCommands;
import pl.corekit.command.commands.PlayerUtilityCommands;
import pl.corekit.command.commands.SpawnCommands;
import pl.corekit.lang.MessageService;

import java.util.List;

/**
 * Central hook into Paper's {@code LifecycleEvents.COMMANDS} event. It owns no
 * command logic itself — it simply collects every {@link CoreKitCommand} and
 * registers them together when the registrar becomes available.
 */
public final class CommandRegistrar {

    private final CoreKitPlugin plugin;
    private final List<CoreKitCommand> commands;

    public CommandRegistrar(CoreKitPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.commands = List.of(
                new CoreCommand(plugin, messages),
                new PlayerUtilityCommands(plugin, messages, plugin.god()),
                new GameModeCommands(plugin, messages),
                new HomeCommands(plugin, messages, plugin.homes()),
                new SpawnCommands(plugin, messages, plugin.spawn())
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
