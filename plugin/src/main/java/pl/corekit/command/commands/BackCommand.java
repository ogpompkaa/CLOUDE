package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.feature.back.BackService;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.feature.teleport.TeleportService;

import java.util.List;

/** {@code /back} — return to your previous location (or death point). */
public final class BackCommand implements CoreKitCommand {

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final BackService back;
    private final TeleportService teleport;

    public BackCommand(CoreKitPlugin plugin, FeedbackService feedback,
                       BackService back, TeleportService teleport) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.back = back;
        this.teleport = teleport;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(plugin.getPluginMeta(),
                Commands.literal("back")
                        .requires(s -> s.getSender().hasPermission("corekit.back"))
                        .executes(this::back)
                        .build(),
                "Return to your previous location", List.of());
    }

    private int back(CommandContext<CommandSourceStack> ctx) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player == null) {
            feedback.error(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        Location destination = back.previous(player.getUniqueId());
        if (destination == null) {
            feedback.error(player, "back.none");
            return 0;
        }
        teleport.request(player, destination, "back");
        return Command.SINGLE_SUCCESS;
    }
}
