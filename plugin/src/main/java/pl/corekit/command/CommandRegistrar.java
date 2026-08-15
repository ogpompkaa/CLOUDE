package pl.corekit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.lang.MessageService;
import pl.corekit.storage.PlayerProfile;

import java.time.Duration;
import java.util.List;

/**
 * Registers CoreKit's commands through Paper's modern Brigadier command API
 * (the {@code LifecycleEvents.COMMANDS} hook), rather than the legacy
 * {@code plugin.yml} {@code commands} block. This gives real subcommand trees,
 * per-node permission gating and native tab-completion for free.
 *
 * <p>Command surface:
 * <pre>
 *   /corekit             → usage
 *   /corekit reload      → reload config + language files      (corekit.command.reload)
 *   /corekit info        → plugin/version info
 *   /corekit profile     → show the caller's stored profile    (players only)
 * </pre>
 */
public final class CommandRegistrar {

    private final CoreKitPlugin plugin;
    private final MessageService messages;

    public CommandRegistrar(CoreKitPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(
                    plugin.getPluginMeta(),
                    buildRoot(),
                    "CoreKit administration command",
                    List.of("ck"));
        });
    }

    private LiteralCommandNode<CommandSourceStack> buildRoot() {
        return Commands.literal("corekit")
                .requires(source -> source.getSender().hasPermission("corekit.command"))
                .executes(context -> {
                    messages.send(context.getSource().getSender(), "command.usage");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission("corekit.command.reload"))
                        .executes(this::reload))
                .then(Commands.literal("info")
                        .executes(this::info))
                .then(Commands.literal("profile")
                        .executes(this::profile))
                .build();
    }

    private int reload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        plugin.reload();
        messages.send(context.getSource().getSender(), "command.reloaded");
        return Command.SINGLE_SUCCESS;
    }

    private int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        messages.send(context.getSource().getSender(), "command.info",
                MessageService.placeholder("version", plugin.getPluginMeta().getVersion()));
        return Command.SINGLE_SUCCESS;
    }

    private int profile(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return 0;
        }

        // Async read → format on the DB thread → deliver on the main thread.
        plugin.profiles().find(player.getUniqueId()).thenAccept(optional ->
                plugin.database().sync(() -> {
                    if (optional.isEmpty()) {
                        messages.send(player, "command.profile.none");
                        return;
                    }
                    PlayerProfile profile = optional.get();
                    messages.send(player, "command.profile.header");
                    messages.send(player, "command.profile.playtime",
                            MessageService.placeholder("playtime", format(profile.playtime())));
                })
        ).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to load profile for {}",
                    player.getName(), throwable);
            plugin.database().sync(() ->
                    player.sendMessage(messages.render("command.error")
                            .color(NamedTextColor.RED)));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }

    private static String format(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
