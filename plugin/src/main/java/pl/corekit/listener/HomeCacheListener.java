package pl.corekit.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.corekit.feature.home.HomeService;

/**
 * Keeps the per-player home-name cache warm for online players so home
 * tab-completion never has to hit the database.
 */
public final class HomeCacheListener implements Listener {

    private final HomeService homes;

    public HomeCacheListener(HomeService homes) {
        this.homes = homes;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        homes.warmCache(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homes.evictCache(event.getPlayer().getUniqueId());
    }
}
