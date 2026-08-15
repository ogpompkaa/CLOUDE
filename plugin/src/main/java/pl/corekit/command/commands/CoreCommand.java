package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.lang.MessageService;
import pl.corekit.storage.PlayerProfile;

import java.time.Duration;
import java.util.List;

/** {@code /corekit reload|info|profile} — administration and diagnostics. */
public final class CoreCommand implements CoreKitCommand {

    private final CoreKitPlugin plugin;
    private final MessageService messages;

    public CoreCommand(CoreKitPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(
                plugin.getPluginMeta(),
                Commands.literal("corekit")
                        .requires(s -> s.getSender().hasPermission("corekit.command"))
                        .executes(this::usage)
                        .then(Commands.literal("reload")
                                .requires(s -> s.getSender().hasPermission("corekit.command.reload"))
                                .executes(this::reload))
                        .then(Commands.literal("info").executes(this::info))
                        .then(Commands.literal("profile").executes(this::profile))
                        .build(),
                "CoreKit administration command",
                List.of("ck"));
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        messages.send(context.getSource().getSender(), "command.usage");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reload();
        messages.send(context.getSource().getSender(), "command.reloaded");
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        messages.send(context.getSource().getSender(), "command.info",
                MessageService.placeholder("version", plugin.getPluginMeta().getVersion()));
        return Command.SINGLE_SUCCESS;
    }

    private int profile(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player player = CommandUtil.asPlayer(context);
        if (player == null) {
            messages.send(sender, "players-only");
            return 0;
        }

        plugin.profiles().find(player.getUniqueId()).thenAccept(optional ->
                plugin.database().sync(() -> {
                    if (optional.isEmpty()) {
                        messages.send(player, "command.profile.none");
                        return;
                    }
                    PlayerProfile profile = optional.get();
                    messages.send(player, "command.profile.header");
                    messages.send(player, "command.profile.playtime",
                            MessageService.placeholder("playtime", formatPlaytime(profile.playtime())));
                })
        ).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to load profile for {}", player.getName(), throwable);
            plugin.database().sync(() ->
                    player.sendMessage(messages.render("command.error").color(NamedTextColor.RED)));
            return null;
        });
        return Command.SINGLE_SUCCESS;
    }

    private static String formatPlaytime(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
