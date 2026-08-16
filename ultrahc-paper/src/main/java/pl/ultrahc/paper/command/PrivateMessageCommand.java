package pl.ultrahc.paper.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prywatne wiadomosci: /msg &lt;gracz&gt; &lt;tresc&gt; oraz /r (odpowiedz ostatniemu
 * rozmowcy). Format i dzwiek konfigurowalne. Kolory w tresci dla graczy z perkiem
 * ultrahc.chat.color. Obsluguje obie komendy (rozroznienie po nazwie).
 */
public class PrivateMessageCommand implements CommandExecutor, TabCompleter {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, UUID> lastConversation = new ConcurrentHashMap<>();

    public PrivateMessageCommand(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().prefixed("general.players-only", null));
            return true;
        }
        boolean reply = command.getName().equalsIgnoreCase("r");

        Player target;
        String message;
        if (reply) {
            if (args.length < 1) { player.sendMessage(msg("msg.usage-reply")); return true; }
            UUID last = lastConversation.get(player.getUniqueId());
            target = last != null ? plugin.getServer().getPlayer(last) : null;
            if (target == null) { player.sendMessage(msg("msg.no-reply")); return true; }
            message = String.join(" ", args);
        } else {
            if (args.length < 2) { player.sendMessage(msg("msg.usage")); return true; }
            target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(msg("msg.no-target")); return true; }
            message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (target.equals(player)) { player.sendMessage(msg("msg.self")); return true; }

        // Kolory w tresci dla perku, inaczej zwykly tekst.
        Component body = player.hasPermission("ultrahc.chat.color")
                ? LEGACY.deserialize(message) : Component.text(message);

        player.sendMessage(line("msg.format-out", target.getName(), body));
        target.sendMessage(line("msg.format-in", player.getName(), body));
        target.playSound(target.getLocation(), sound(), 1.0f, 1.4f);

        // Zapamietaj rozmowe w obie strony (do /r).
        lastConversation.put(player.getUniqueId(), target.getUniqueId());
        lastConversation.put(target.getUniqueId(), player.getUniqueId());
        return true;
    }

    private Component line(String key, String other, Component body) {
        Component head = LEGACY.deserialize(plugin.messages().raw(key, Map.of("player", other)));
        return head.append(body);
    }

    private Component msg(String key) {
        return plugin.messages().prefixed(key, null);
    }

    private Sound sound() {
        String name = plugin.configManager().raw().getString("chat.msg-sound", "entity.experience_orb.pickup");
        Sound s = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT)));
        return s != null ? s : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("r") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        return out;
    }
}
