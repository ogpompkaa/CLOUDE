package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.feature.teleport.TeleportRequest.Direction;
import pl.corekit.feature.teleport.TeleportRequestService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Teleport-request commands: {@code /tpa} and {@code /tpahere} to send,
 * {@code /tpaccept} and {@code /tpdeny} to respond. Accept/deny take an optional
 * player name (tab-completed to the pending requester) so it's unambiguous which
 * request you're answering.
 */
public final class TeleportRequestCommands implements CoreKitCommand {

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final TeleportRequestService requests;

    public TeleportRequestCommands(CoreKitPlugin plugin, FeedbackService feedback,
                                   TeleportRequestService requests) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.requests = requests;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(plugin.getPluginMeta(), send("tpa", "corekit.tpa", Direction.GO),
                "Request to teleport to a player", List.of());
        registrar.register(plugin.getPluginMeta(), send("tpahere", "corekit.tpahere", Direction.SUMMON),
                "Request a player to teleport to you", List.of());
        registrar.register(plugin.getPluginMeta(), respond("tpaccept", true), "Accept a teleport request", List.of());
        registrar.register(plugin.getPluginMeta(), respond("tpdeny", false), "Deny a teleport request", List.of());
    }

    private LiteralCommandNode<CommandSourceStack> send(String label, String permission, Direction direction) {
        return Commands.literal(label)
                .requires(s -> s.getSender().hasPermission(permission))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(ctx -> {
                            Player sender = CommandUtil.asPlayer(ctx);
                            if (sender == null) {
                                feedback.error(ctx.getSource().getSender(), "players-only");
                                return 0;
                            }
                            Player target = CommandUtil.resolveTarget(ctx, "target");
                            requests.send(sender, target, direction);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> respond(String label, boolean accept) {
        return Commands.literal(label)
                .requires(s -> s.getSender().hasPermission("corekit.tpa"))
                .executes(ctx -> respond(ctx, accept, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPendingSender)
                        .executes(ctx -> respond(ctx, accept, StringArgumentType.getString(ctx, "player"))))
                .build();
    }

    private int respond(CommandContext<CommandSourceStack> ctx, boolean accept, String sender) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player == null) {
            feedback.error(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        if (accept) {
            requests.accept(player, sender);
        } else {
            requests.deny(player, sender);
        }
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestPendingSender(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player != null) {
            String pending = requests.pendingSenderName(player.getUniqueId());
            if (pending != null && pending.toLowerCase(java.util.Locale.ROOT)
                    .startsWith(builder.getRemaining().toLowerCase(java.util.Locale.ROOT))) {
                builder.suggest(pending);
            }
        }
        return builder.buildFuture();
    }
}
