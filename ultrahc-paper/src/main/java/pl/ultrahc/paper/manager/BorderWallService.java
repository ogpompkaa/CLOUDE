package pl.ultrahc.paper.manager;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.GameState;

import java.util.UUID;

/**
 * Widoczna "sciana" czerwonych czastek wzdluz granicy w poblizu graczy — widac,
 * jak krawedz napiera podczas kurczenia. Sterowane effects.border-wall.
 */
public class BorderWallService {

    private final UltraHcPlugin plugin;
    private BukkitTask task;

    public BorderWallService(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = Math.max(4, plugin.configManager().raw().getInt("effects.border-wall-interval-ticks", 10));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, interval);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        var cfg = plugin.configManager().raw();
        if (!cfg.getBoolean("effects.border-wall", true) || plugin.games() == null) return;
        GameInstance game = plugin.games().current();
        if (game == null || game.state() != GameState.RUNNING) return;

        World w = game.world();
        var border = w.getWorldBorder();
        double half = border.getSize() / 2.0;
        double cx = border.getCenter().getX();
        double cz = border.getCenter().getZ();
        double minX = cx - half, maxX = cx + half, minZ = cz - half, maxZ = cz + half;
        double range = cfg.getDouble("effects.border-wall-distance", 8);
        Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.3f);

        for (UUID id : game.participants()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || p.getWorld() != w || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            double px = l.getX(), pz = l.getZ(), py = l.getY();
            if (Math.abs(px - minX) < range) column(w, minX, py, pz, dust);
            if (Math.abs(px - maxX) < range) column(w, maxX, py, pz, dust);
            if (Math.abs(pz - minZ) < range) column(w, px, py, minZ, dust);
            if (Math.abs(pz - maxZ) < range) column(w, px, py, maxZ, dust);
        }
    }

    /** Pionowy slupek czastek (fragment sciany) w danym punkcie krawedzi. */
    private void column(World w, double x, double baseY, double z, Particle.DustOptions dust) {
        for (double dy = -2; dy <= 3; dy += 1) {
            w.spawnParticle(Particle.DUST, new Location(w, x, baseY + dy, z), 1, 0, 0, 0, 0, dust);
        }
    }
}
