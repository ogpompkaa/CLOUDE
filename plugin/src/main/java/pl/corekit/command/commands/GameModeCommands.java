package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.lang.MessageService;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /gamemode <mode> [player]} plus the familiar {@code /gmc /gms /gma
 * /gmsp} shortcuts. Modes accept names, single-letter aliases and the vanilla
 * 0-3 numeric ids.
 */
public final class GameModeCommands implements CoreKitCommand {

    private static final List<String> SUGGESTIONS =
            List.of("survival", "creative", "adventure", "spectator");

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;

    public GameModeCommands(CoreKitPlugin plugin, FeedbackService feedback) {
        this.plugin = plugin;
        this.feedback = feedback;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(plugin.getPluginMeta(), fullCommand(), "Change game mode", List.of("gm"));
        registrar.register(plugin.getPluginMeta(), shortcut("gmc", GameMode.CREATIVE), "Creative mode", List.of());
        registrar.register(plugin.getPluginMeta(), shortcut("gms", GameMode.SURVIVAL), "Survival mode", List.of());
        registrar.register(plugin.getPluginMeta(), shortcut("gma", GameMode.ADVENTURE), "Adventure mode", List.of());
        registrar.register(plugin.getPluginMeta(), shortcut("gmsp", GameMode.SPECTATOR), "Spectator mode", List.of());
    }

    private LiteralCommandNode<CommandSourceStack> fullCommand() {
        return Commands.literal("gamemode")
                .requires(s -> s.getSender().hasPermission("corekit.gamemode"))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(this::suggestModes)
                        .executes(ctx -> {
                            GameMode mode = parse(StringArgumentType.getString(ctx, "mode"));
                            return applySelf(ctx, mode, StringArgumentType.getString(ctx, "mode"));
                        })
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .requires(s -> s.getSender().hasPermission("corekit.gamemode.others"))
                                .executes(ctx -> {
                                    GameMode mode = parse(StringArgumentType.getString(ctx, "mode"));
                                    return applyOther(ctx, mode, StringArgumentType.getString(ctx, "mode"));
                                })))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> shortcut(String name, GameMode mode) {
        return Commands.literal(name)
                .requires(s -> s.getSender().hasPermission("corekit.gamemode"))
                .executes(ctx -> applySelf(ctx, mode, mode.name()))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(s -> s.getSender().hasPermission("corekit.gamemode.others"))
                        .executes(ctx -> applyOther(ctx, mode, mode.name())))
                .build();
    }

    private int applySelf(CommandContext<CommandSourceStack> ctx, GameMode mode, String input) {
        CommandSender sender = ctx.getSource().getSender();
        if (mode == null) {
            feedback.error(sender, "gamemode.invalid", MessageService.placeholder("input", input));
            return 0;
        }
        Player self = CommandUtil.asPlayer(ctx);
        if (self == null) {
            feedback.error(sender, "players-only");
            return 0;
        }
        self.setGameMode(mode);
        feedback.quick(self, true, "gamemode.self", MessageService.placeholder("mode", label(mode)));
        return Command.SINGLE_SUCCESS;
    }

    private int applyOther(CommandContext<CommandSourceStack> ctx, GameMode mode, String input)
            throws CommandSyntaxException {
        CommandSender actor = ctx.getSource().getSender();
        if (mode == null) {
            feedback.error(actor, "gamemode.invalid", MessageService.placeholder("input", input));
            return 0;
        }
        Player target = CommandUtil.resolveTarget(ctx, "target");
        target.setGameMode(mode);
        feedback.success(actor, "gamemode.other",
                MessageService.placeholder("target", target.getName()),
                MessageService.placeholder("mode", label(mode)));
        if (!target.equals(actor)) {
            feedback.quick(target, true, "gamemode.self", MessageService.placeholder("mode", label(mode)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestModes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String mode : SUGGESTIONS) {
            if (mode.startsWith(remaining)) {
                builder.suggest(mode);
            }
        }
        return builder.buildFuture();
    }

    private static String label(GameMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /** Parses a mode from a name, single-letter alias or 0-3 id; {@code null} if invalid. */
    private static GameMode parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }
}
