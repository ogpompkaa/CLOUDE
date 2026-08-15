package pl.corekit.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.Listener;
import pl.corekit.CoreKitPlugin;
import pl.corekit.feature.back.BackService;

/**
 * Feeds {@link BackService}: records the origin before every teleport (so
 * {@code /back} toggles you to where you were, and back again), and the death
 * location on death when {@code back.on-death} is enabled. Clears state on quit.
 */
public final class BackListener implements Listener {

    private final CoreKitPlugin plugin;
    private final BackService back;

    public BackListener(CoreKitPlugin plugin, BackService back) {
        this.plugin = plugin;
        this.back = back;
    }

    // MONITOR + ignoreCancelled: only remember teleports that actually happen.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != null) {
            back.remember(event.getPlayer().getUniqueId(), event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.configManager().settings().backOnDeath()) {
            back.remember(event.getEntity().getUniqueId(), event.getEntity().getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        back.clear(event.getPlayer().getUniqueId());
    }
}
