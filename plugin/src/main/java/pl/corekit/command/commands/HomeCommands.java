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
import pl.corekit.feature.home.HomeService;
import pl.corekit.lang.MessageService;
import pl.corekit.storage.HomeRepository;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The homes suite: {@code /sethome [name]}, {@code /home [name]},
 * {@code /delhome [name]}, {@code /homes}. All persistence is async; the only
 * work forced onto the main thread is resolving a {@link Location} and
 * teleporting, both of which must be.
 */
public final class HomeCommands implements CoreKitCommand {

    private static final String DEFAULT_NAME = "home";

    private final CoreKitPlugin plugin;
    private final MessageService messages;
    private final HomeService homes;
    private final HomeRepository repository;

    public HomeCommands(CoreKitPlugin plugin, MessageService messages, HomeService homes) {
        this.plugin = plugin;
        this.messages = messages;
        this.homes = homes;
        this.repository = homes.repository();
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
            messages.send(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        String canonical = name.toLowerCase(Locale.ROOT);
        boolean isNew = !homes.cachedNames(player.getUniqueId()).contains(canonical);
        int limit = homes.homeLimit(player);
        if (isNew && homes.cachedNames(player.getUniqueId()).size() >= limit) {
            messages.send(player, "home.limit-reached",
                    MessageService.placeholder("limit", String.valueOf(limit)));
            return 0;
        }

        Location location = player.getLocation();
        repository.save(player.getUniqueId(), name, location).thenRun(() ->
                plugin.database().sync(() -> {
                    homes.rememberName(player.getUniqueId(), name);
                    messages.send(player, "home.set", MessageService.placeholder("name", canonical));
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
            messages.send(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        String canonical = name.toLowerCase(Locale.ROOT);
        repository.find(player.getUniqueId(), name).thenAccept(optional ->
                plugin.database().sync(() -> {
                    if (optional.isEmpty()) {
                        messages.send(player, "home.not-found",
                                MessageService.placeholder("name", canonical));
                        return;
                    }
                    Location location = optional.get().toLocation();
                    if (location == null) {
                        messages.send(player, "home.world-missing",
                                MessageService.placeholder("name", canonical));
                        return;
                    }
                    messages.send(player, "home.teleporting",
                            MessageService.placeholder("name", canonical));
                    player.teleportAsync(location);
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
            messages.send(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        String canonical = name.toLowerCase(Locale.ROOT);
        repository.delete(player.getUniqueId(), name).thenAccept(removed ->
                plugin.database().sync(() -> {
                    if (removed) {
                        homes.forgetName(player.getUniqueId(), name);
                        messages.send(player, "home.deleted",
                                MessageService.placeholder("name", canonical));
                    } else {
                        messages.send(player, "home.not-found",
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
                        messages.send(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    int limit = homes.homeLimit(player);
                    repository.findAll(player.getUniqueId()).thenAccept(list ->
                            plugin.database().sync(() -> {
                                if (list.isEmpty()) {
                                    messages.send(player, "home.list-empty");
                                    return;
                                }
                                messages.send(player, "home.list-header",
                                        MessageService.placeholder("count", String.valueOf(list.size())),
                                        MessageService.placeholder("limit", limitLabel(limit)));
                                list.forEach(h -> messages.send(player, "home.list-entry",
                                        MessageService.placeholder("name", h.name())));
                            })
                    ).exceptionally(logAndReport(player, "list homes"));
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

    private static String limitLabel(int limit) {
        return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
    }

    /** Logs a failed async DB op and tells the player something went wrong. */
    private java.util.function.Function<Throwable, Void> logAndReport(Player player, String action) {
        return throwable -> {
            plugin.getSLF4JLogger().warn("Failed to {} for {}", action, player.getName(), throwable);
            plugin.database().sync(() -> messages.send(player, "command.error"));
            return null;
        };
    }
}
