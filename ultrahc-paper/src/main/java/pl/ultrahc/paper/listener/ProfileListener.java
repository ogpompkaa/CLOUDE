package pl.ultrahc.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.ultrahc.paper.UltraHcPlugin;

/** Laduje profil i questy przy wejsciu, zapisuje/zwalnia przy wyjsciu gracza. */
public class ProfileListener implements Listener {

    private final UltraHcPlugin plugin;

    public ProfileListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        var uuid = player.getUniqueId();
        plugin.profiles().loadAsync(uuid, player.getName());
        if (plugin.quests() != null) plugin.quests().loadAsync(uuid);

        // Kosmetyka: sformatowany komunikat wejscia (z ranga) + powitanie (w lobby).
        String rank = plugin.groups() != null ? plugin.groups().groupPrefix(player) : "";
        e.joinMessage(plugin.messages().component("join-message",
                java.util.Map.of("player", player.getName(), "rank", rank)));
        if (plugin.role() == pl.ultrahc.paper.ServerRole.LOBBY) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                pl.ultrahc.paper.util.Feedback.title(player,
                        plugin.messages().component("welcome.main", null),
                        plugin.messages().component("welcome.sub",
                                java.util.Map.of("player", player.getName())));
                pl.ultrahc.paper.util.Feedback.joinRing(player); // efekt wejscia do huba
                // Przedmiot menu w hotbarze (kompas otwiera Hub).
                var compass = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
                var cm = compass.getItemMeta();
                cm.displayName(plugin.messages().component("menu-item", null));
                compass.setItemMeta(cm);
                player.getInventory().setItem(4, compass);
            }, 15L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var uuid = e.getPlayer().getUniqueId();
        String rank = plugin.groups() != null ? plugin.groups().groupPrefix(e.getPlayer()) : "";
        e.quitMessage(plugin.messages().component("quit-message",
                java.util.Map.of("player", e.getPlayer().getName(), "rank", rank)));
        plugin.profiles().saveAndUnloadAsync(uuid);
        if (plugin.quests() != null) plugin.quests().unload(uuid);
        if (plugin.compass() != null) plugin.compass().clear(uuid);
        if (plugin.ranks() != null) plugin.ranks().cleanup(e.getPlayer());
        if (plugin.scoreboard() != null) plugin.scoreboard().clear(uuid);
        if (plugin.party() != null) plugin.party().handleQuit(e.getPlayer());
    }
}
