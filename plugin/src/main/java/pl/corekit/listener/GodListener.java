package pl.corekit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.corekit.feature.god.GodService;

/**
 * Enforces god mode by cancelling all damage to players who have it enabled,
 * and clears their flag on quit so the set cannot leak across sessions.
 */
public final class GodListener implements Listener {

    private final GodService god;

    public GodListener(GodService god) {
        this.god = god;
    }

    // LOW so other plugins still see the (about-to-be-cancelled) event; we only
    // care about zeroing the final damage. ignoreCancelled avoids redundant work.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && god.isEnabled(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        god.disable(event.getPlayer().getUniqueId());
    }
}
