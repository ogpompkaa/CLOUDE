package pl.ultrahc.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.ultrahc.paper.UltraHcPlugin;

/** /pc <wiadomosc> — czat party (tylko do czlonkow party). */
public class PartyChatCommand implements CommandExecutor {

    private final UltraHcPlugin plugin;

    public PartyChatCommand(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().prefixed("general.players-only", null));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.messages().prefixed("general.usage", java.util.Map.of("usage", "/pc <wiadomosc>")));
            return true;
        }
        plugin.party().chat(player, String.join(" ", args));
        return true;
    }
}
