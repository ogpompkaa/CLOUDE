package pl.ultrahc.paper.manager;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.ConfigManager;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.Team;

import java.util.UUID;

/**
 * Kurczenie granicy w 3 fazach + arenka (sudden-death) — patrz DECYZJE, sekcja 2.
 * Faza 1: 24 bloki/min (min 10-30). Faza 2: 30/min (min 30-45).
 * Faza 3: od 45 min teleport zywych na arenke, ktora zaciska sie do ~0.
 *
 * <p>Wartosci docelowe liczone z tempa w config (nic nie hardkodowane), przez
 * {@link WorldBorder#setSize(double, long)} (plynna interpolacja liniowa).
 */
public class BorderManager {

    private final UltraHcPlugin plugin;

    public BorderManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cfg() { return plugin.configManager(); }

    /** Faza 1 (od shrink-start-min): kurczenie tempem blocks-per-min do accelerate-min. */
    public void beginPhase1(GameInstance game) {
        var c = cfg().raw();
        double start = c.getDouble("border.start", 1000);
        double perMin = c.getDouble("border.blocks-per-min", 24);
        int shrinkStart = c.getInt("border.shrink-start-min", 10);
        int accelerate = c.getInt("border.accelerate-min", 30);

        int minutes = Math.max(1, accelerate - shrinkStart);
        double target = Math.max(1, start - perMin * minutes);
        game.world().getWorldBorder().setSize(target, minutes * 60L);
        plugin.getLogger().info("[UltraHC] Granica faza 1: " + start + " -> " + target + " w " + minutes + " min.");
    }

    /** Faza 2 (od accelerate-min): przyspieszone tempo blocks-per-min-fast do teleport-min. */
    public void beginPhase2(GameInstance game) {
        var c = cfg().raw();
        double start = c.getDouble("border.start", 1000);
        double perMin = c.getDouble("border.blocks-per-min", 24);
        double perMinFast = c.getDouble("border.blocks-per-min-fast", 30);
        int shrinkStart = c.getInt("border.shrink-start-min", 10);
        int accelerate = c.getInt("border.accelerate-min", 30);
        int teleport = c.getInt("arena-showdown.teleport-min", 45);

        double sizeAtAccelerate = Math.max(1, start - perMin * (accelerate - shrinkStart));
        int minutes = Math.max(1, teleport - accelerate);
        double target = Math.max(1, sizeAtAccelerate - perMinFast * minutes);
        game.world().getWorldBorder().setSize(target, minutes * 60L);
        plugin.getLogger().info("[UltraHC] Granica faza 2 (przyspieszenie): -> " + target + " w " + minutes + " min.");
    }

    /** Faza 3 (od teleport-min): arenka — recenter na spawn, zacisk do rozmiaru arenki, TP zywych, kolaps do ~0. */
    public void beginShowdown(GameInstance game) {
        var c = cfg().raw();
        String mode = c.getString("arena-showdown.mode", "BORDER_CLAMP");
        double size = c.getDouble("arena-showdown.size", 40);
        int teleport = c.getInt("arena-showdown.teleport-min", 45);
        int collapseTo = c.getInt("arena-showdown.collapse-to-min", 60);

        World world = game.world();
        WorldBorder border = world.getWorldBorder();
        Location center = world.getSpawnLocation();
        border.setCenter(center);
        border.setSize(size); // natychmiastowy zacisk do arenki

        // Teleport wszystkich zywych do srodka arenki.
        if (game.teams() != null) {
            for (Team team : game.teams().aliveTeams()) {
                for (UUID id : team.getAlive()) {
                    Player p = plugin.getServer().getPlayer(id);
                    if (p != null) {
                        Location loc = world.getHighestBlockAt(center).getLocation().add(0.5, 1, 0.5);
                        p.teleport(loc);
                    }
                }
            }
        }
        // Kolaps arenki do ~0 -> sudden-death zawsze sie rozstrzyga.
        int minutes = Math.max(1, collapseTo - teleport);
        border.setSize(1, minutes * 60L);
        plugin.getLogger().info("[UltraHC] Arenka (" + mode + "): rozmiar " + size + ", kolaps do ~0 w " + minutes + " min.");
    }
}
