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
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.feature.spawn.SpawnService;
import pl.corekit.feature.teleport.TeleportService;

import java.util.List;

/** {@code /spawn} (warm-up teleport) and {@code /setspawn}. */
public final class SpawnCommands implements CoreKitCommand {

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final SpawnService spawn;
    private final TeleportService teleport;

    public SpawnCommands(CoreKitPlugin plugin, FeedbackService feedback,
                         SpawnService spawn, TeleportService teleport) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.spawn = spawn;
        this.teleport = teleport;
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
            feedback.error(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        teleport.request(player, spawn.resolve(), "spawn");
        return Command.SINGLE_SUCCESS;
    }

    private LiteralCommandNode<CommandSourceStack> setSpawnCommand() {
        return Commands.literal("setspawn")
                .requires(s -> s.getSender().hasPermission("corekit.setspawn"))
                .executes(ctx -> {
                    Player player = CommandUtil.asPlayer(ctx);
                    if (player == null) {
                        feedback.error(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    spawn.setSpawn(player.getLocation());
                    feedback.success(player, "spawn.set");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
