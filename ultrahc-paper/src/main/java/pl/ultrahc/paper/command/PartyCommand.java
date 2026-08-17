package pl.ultrahc.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.List;

/** Komenda /party: invite/accept/deny/leave/kick/disband/list. */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of(
            "invite", "accept", "deny", "leave", "kick", "disband", "list");

    private final UltraHcPlugin plugin;

    public PartyCommand(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().prefixed("general.players-only", null));
            return true;
        }
        var party = plugin.party();
        if (args.length == 0) { plugin.partyGui().open(player); return true; }
        switch (args[0].toLowerCase()) {
            case "invite", "add" -> {
                if (args.length < 2) usage(player, "/party invite <nick>");
                else party.invite(player, args[1]);
            }
            case "accept", "join" -> party.accept(player);
            case "deny", "decline" -> party.deny(player);
            case "leave" -> party.leave(player);
            case "kick" -> {
                if (args.length < 2) usage(player, "/party kick <nick>");
                else party.kick(player, args[1]);
            }
            case "disband" -> party.disband(player);
            case "list" -> party.list(player);
            default -> usage(player, "/party <invite|accept|deny|leave|kick|disband|list>");
        }
        return true;
    }

    private void usage(Player p, String syntax) {
        p.sendMessage(plugin.messages().prefixed("general.usage", java.util.Map.of("usage", syntax)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], SUB, new ArrayList<>());
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            List<String> names = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) names.add(p.getName());
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        return new ArrayList<>();
    }
}
