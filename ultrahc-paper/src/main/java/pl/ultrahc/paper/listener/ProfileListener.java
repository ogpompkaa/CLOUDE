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
        var uuid = e.getPlayer().getUniqueId();
        plugin.profiles().loadAsync(uuid, e.getPlayer().getName());
        if (plugin.quests() != null) plugin.quests().loadAsync(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var uuid = e.getPlayer().getUniqueId();
        plugin.profiles().saveAndUnloadAsync(uuid);
        if (plugin.quests() != null) plugin.quests().unload(uuid);
        if (plugin.compass() != null) plugin.compass().clear(uuid);
        if (plugin.ranks() != null) plugin.ranks().cleanup(e.getPlayer());
        if (plugin.scoreboard() != null) plugin.scoreboard().clear(uuid);
    }
}
