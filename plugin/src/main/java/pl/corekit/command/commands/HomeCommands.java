package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.feature.home.HomeGui;
import pl.corekit.feature.home.HomeService;
import pl.corekit.feature.teleport.TeleportService;
import pl.corekit.lang.MessageService;
import pl.corekit.storage.HomeRepository;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The homes suite: {@code /sethome [name]}, {@code /home [name]},
 * {@code /delhome [name]}, {@code /homes}. Persistence is async; {@code /home}
 * routes through {@link TeleportService} for the warm-up/cooldown flow, and
 * {@code /homes} opens the {@link HomeGui} chest menu (teleport / delete with
 * confirmation).
 */
public final class HomeCommands implements CoreKitCommand {

    private static final String DEFAULT_NAME = "home";

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final HomeService homes;
    private final HomeRepository repository;
    private final TeleportService teleport;
    private final HomeGui gui;

    public HomeCommands(CoreKitPlugin plugin, FeedbackService feedback,
                        HomeService homes, TeleportService teleport, HomeGui gui) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.homes = homes;
        this.repository = homes.repository();
        this.teleport = teleport;
        this.gui = gui;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(plugin.getPluginMeta(), setHome(), "Set a home", List.of());
        registrar.register(plugin.getPluginMeta(), home(), "Teleport to a home", List.of());
        registrar.register(plugin.getPluginMeta(), delHome(), "Delete a home", List.of());
        registrar.register(plugin.getPluginMeta(), listHomes(), "List your homes", List.of());
    }

    // -------------------------------------------------------------- /sethome

    private LiteralCommandNode<CommandSourceStack> setHome() {
        return Commands.literal("sethome")
                .requires(s -> s.getSender().hasPermission("corekit.home"))
                .executes(ctx -> doSetHome(ctx, DEFAULT_NAME))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> doSetHome(ctx, StringArgumentType.getString(ctx, "name"))))
                .build();
    }

    private int doSetHome(CommandContext<CommandSourceStack> ctx, String name) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player == null) {
            feedback.error(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        String canonical = name.toLowerCase(Locale.ROOT);
        boolean isNew = !homes.cachedNames(player.getUniqueId()).contains(canonical);
        int limit = homes.homeLimit(player);
        if (isNew && homes.cachedNames(player.getUniqueId()).size() >= limit) {
            feedback.error(player, "home.limit-reached",
                    MessageService.placeholder("limit", String.valueOf(limit)));
            return 0;
        }

        Location location = player.getLocation();
        repository.save(player.getUniqueId(), name, location).thenRun(() ->
                plugin.database().sync(() -> {
                    homes.rememberName(player.getUniqueId(), name);
                    feedback.success(player, "home.set", MessageService.placeholder("name", canonical));
                })
        ).exceptionally(logAndReport(player, "save home"));
        return Command.SINGLE_SUCCESS;
    }

    // ----------------------------------------------------------------- /home

    private LiteralCommandNode<CommandSourceStack> home() {
        return Commands.literal("home")
                .requires(s -> s.getSender().hasPermission("corekit.home"))
                .executes(ctx -> doHome(ctx, DEFAULT_NAME))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(this::suggestHomes)
                        .executes(ctx -> doHome(ctx, StringArgumentType.getString(ctx, "name"))))
                .build();
    }

    private int doHome(CommandContext<CommandSourceStack> ctx, String name) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player == null) {
            feedback.error(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        String canonical = name.toLowerCase(Locale.ROOT);
        repository.find(player.getUniqueId(), name).thenAccept(optional ->
                plugin.database().sync(() -> {
                    if (optional.isEmpty()) {
                        feedback.error(player, "home.not-found",
                                MessageService.placeholder("name", canonical));
                        return;
                    }
                    Location location = optional.get().toLocation();
                    if (location == null) {
                        feedback.error(player, "home.world-missing",
                                MessageService.placeholder("name", canonical));
                        return;
                    }
                    teleport.request(player, location, canonical);
                })
        ).exceptionally(logAndReport(player, "load home"));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------- /delhome

    private LiteralCommandNode<CommandSourceStack> delHome() {
        return Commands.literal("delhome")
                .requires(s -> s.getSender().hasPermission("corekit.home"))
                .executes(ctx -> doDelHome(ctx, DEFAULT_NAME))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(this::suggestHomes)
                        .executes(ctx -> doDelHome(ctx, StringArgumentType.getString(ctx, "name"))))
                .build();
    }

    private int doDelHome(CommandContext<CommandSourceStack> ctx, String name) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player == null) {
            feedback.error(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        String canonical = name.toLowerCase(Locale.ROOT);
        repository.delete(player.getUniqueId(), name).thenAccept(removed ->
                plugin.database().sync(() -> {
                    if (removed) {
                        homes.forgetName(player.getUniqueId(), name);
                        feedback.success(player, "home.deleted",
                                MessageService.placeholder("name", canonical));
                    } else {
                        feedback.error(player, "home.not-found",
                                MessageService.placeholder("name", canonical));
                    }
                })
        ).exceptionally(logAndReport(player, "delete home"));
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- /homes

    private LiteralCommandNode<CommandSourceStack> listHomes() {
        return Commands.literal("homes")
                .requires(s -> s.getSender().hasPermission("corekit.home"))
                .executes(ctx -> {
                    Player player = CommandUtil.asPlayer(ctx);
                    if (player == null) {
                        feedback.error(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    gui.open(player); // handles the empty case and async fetch itself
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    // --------------------------------------------------------------- helpers

    private CompletableFuture<Suggestions> suggestHomes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Player player = CommandUtil.asPlayer(ctx);
        if (player != null) {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            homes.cachedNames(player.getUniqueId()).stream()
                    .filter(n -> n.startsWith(remaining))
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    /** Logs a failed async DB op and tells the player something went wrong. */
    private java.util.function.Function<Throwable, Void> logAndReport(Player player, String action) {
        return throwable -> {
            plugin.getSLF4JLogger().warn("Failed to {} for {}", action, player.getName(), throwable);
            plugin.database().sync(() -> feedback.error(player, "command.error"));
            return null;
        };
    }
}
