package pl.corekit.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.feature.teleport.TeleportRequestService;
import pl.corekit.feature.teleport.TeleportService;

/**
 * Cancels a pending teleport warm-up when the player moves to a new block or
 * takes damage (both configurable), and clears state on quit. Every handler
 * short-circuits on the cheap {@code isPending} check first, so it adds no
 * measurable cost to the very hot {@link PlayerMoveEvent} for players who aren't
 * teleporting.
 */
public final class TeleportListener implements Listener {

    private final CoreKitPlugin plugin;
    private final TeleportService teleport;
    private final TeleportRequestService requests;

    public TeleportListener(CoreKitPlugin plugin, TeleportService teleport,
                            TeleportRequestService requests) {
        this.plugin = plugin;
        this.teleport = teleport;
        this.requests = requests;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.configManager().settings().cancelOnMove()) {
            return;
        }
        if (!teleport.isPending(event.getPlayer().getUniqueId())) {
            return;
        }
        // Only block-level movement cancels; turning the head does not.
        if (teleport.hasMoved(event.getPlayer().getUniqueId(), event.getTo())) {
            teleport.cancel(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.configManager().settings().cancelOnDamage()) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && teleport.isPending(player.getUniqueId())) {
            teleport.cancel(player.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleport.forget(event.getPlayer().getUniqueId());
        requests.handleQuit(event.getPlayer().getUniqueId());
    }
}
