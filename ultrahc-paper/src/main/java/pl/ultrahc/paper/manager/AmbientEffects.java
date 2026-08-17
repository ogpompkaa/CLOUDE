package pl.ultrahc.paper.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.paper.UltraHcPlugin;

/** Ambientowe czastki wokol NPC i hologramow w lobby (ozywienie sceny). */
public class AmbientEffects {

    private final UltraHcPlugin plugin;
    private BukkitTask task;

    private static final String[] NPC_IDS = {"mietek", "krzysiu", "sklepikarz"};
    private static final String[] HOLO_IDS = {"kills", "wins", "level"};

    public AmbientEffects(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        for (String id : NPC_IDS) {
            Location loc = plugin.readLoc("lobby.npcs." + id);
            if (loc != null) {
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 2.2, 0), 5, 0.3, 0.3, 0.3, 0);
            }
        }
        for (String id : HOLO_IDS) {
            Location loc = plugin.readLoc("lobby.leaderboards.holograms." + id);
            if (loc != null) {
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 0.5, 0), 8, 0.4, 0.4, 0.4, 0.2);
            }
        }
    }
}
