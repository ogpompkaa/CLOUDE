package pl.ultrahc.paper.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Slad czastek za graczami z ranga (perk kosmetyczny). Czastka wg
 * ranks.groups.&lt;id&gt;.trail (pusty = brak). Emitowany tylko gdy gracz sie
 * porusza. Sterowane effects.rank-trails.
 */
public class RankTrailService {

    private final UltraHcPlugin plugin;
    private BukkitTask task;
    private final Map<UUID, Location> last = new HashMap<>();

    public RankTrailService(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = Math.max(2, plugin.configManager().raw().getInt("effects.rank-trail-interval-ticks", 4));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, interval);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        last.clear();
    }

    private void tick() {
        if (!plugin.configManager().raw().getBoolean("effects.rank-trails", true) || plugin.groups() == null) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            String trail = plugin.groups().of(p).trail();
            if (trail == null || trail.isBlank()) { last.remove(p.getUniqueId()); continue; }
            Location prev = last.get(p.getUniqueId());
            Location now = p.getLocation();
            last.put(p.getUniqueId(), now.clone());
            // Tylko gdy faktycznie sie rusza (i w tym samym swiecie).
            if (prev == null || prev.getWorld() != now.getWorld() || prev.distanceSquared(now) < 0.02) continue;
            Particle particle;
            try {
                particle = Particle.valueOf(trail.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }
            now.getWorld().spawnParticle(particle, now.clone().add(0, 0.1, 0), 2, 0.1, 0.02, 0.1, 0);
        }
    }
}
