package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.feature.spawn.SpawnService;
import pl.corekit.lang.MessageService;

import java.util.List;

/** {@code /spawn} and {@code /setspawn}. */
public final class SpawnCommands implements CoreKitCommand {

    private final CoreKitPlugin plugin;
    private final MessageService messages;
    private final SpawnService spawn;

    public SpawnCommands(CoreKitPlugin plugin, MessageService messages, SpawnService spawn) {
        this.plugin = plugin;
        this.messages = messages;
        this.spawn = spawn;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(plugin.getPluginMeta(), spawnCommand(), "Teleport to spawn", List.of());
        registrar.register(plugin.getPluginMeta(), setSpawnCommand(), "Set the server spawn", List.of());
    }

    private LiteralCommandNode<CommandSourceStack> spawnCommand() {
        return Commands.literal("spawn")
                .requires(s -> s.getSender().hasPermission("corekit.spawn"))
                .executes(this::teleportToSpawn)
                .build();
    }

    private int teleportToSpawn(CommandContext<CommandSourceStack> ctx) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player == null) {
            messages.send(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        messages.send(player, "spawn.teleporting");
        player.teleportAsync(spawn.resolve());
        return Command.SINGLE_SUCCESS;
    }

    private LiteralCommandNode<CommandSourceStack> setSpawnCommand() {
        return Commands.literal("setspawn")
                .requires(s -> s.getSender().hasPermission("corekit.setspawn"))
                .executes(ctx -> {
                    Player player = CommandUtil.asPlayer(ctx);
                    if (player == null) {
                        messages.send(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    spawn.setSpawn(player.getLocation());
                    messages.send(player, "spawn.set");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
