package pl.ultrahc.paper.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.GameState;
import pl.ultrahc.paper.game.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kompas: w czasie braku PvP wskazuje sojusznikow; po {@code enemy-compass-unlock-min}
 * mozna przelaczyc tryb na najblizszego przeciwnika (nick ukryty). SOLO: tryb
 * sojusznika nie ma kogo wskazac — od razu proponuje tryb wroga po odblokowaniu.
 */
public class CompassManager {

    private enum Mode { ALLY, ENEMY }

    private final UltraHcPlugin plugin;
    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    public CompassManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Obsluga klikniecia kompasem. */
    public void handleUse(Player player) {
        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        if (game == null || game.state() != GameState.RUNNING || game.teams() == null) return;

        int unlockSec = plugin.configManager().raw().getInt("game.enemy-compass-unlock-min", 18) * 60;
        boolean enemyUnlocked = game.elapsedSeconds() >= unlockSec;

        Mode mode = modes.getOrDefault(player.getUniqueId(), Mode.ALLY);
        if (enemyUnlocked) {
            // Przelaczanie trybu przy kazdym uzyciu.
            mode = (mode == Mode.ALLY) ? Mode.ENEMY : Mode.ALLY;
            modes.put(player.getUniqueId(), mode);
        } else {
            mode = Mode.ALLY;
        }

        if (mode == Mode.ENEMY) {
            pointToNearestEnemy(player, game);
        } else {
            pointToNearestAlly(player, game, enemyUnlocked, unlockSec);
        }
    }

    private void pointToNearestAlly(Player player, GameInstance game, boolean enemyUnlocked, int unlockSec) {
        Team team = game.teams().getTeam(player.getUniqueId());
        Player nearest = null;
        double best = Double.MAX_VALUE;
        if (team != null) {
            for (UUID id : team.getAlive()) {
                if (id.equals(player.getUniqueId())) continue;
                Player mate = plugin.getServer().getPlayer(id);
                if (mate == null || mate.getWorld() != player.getWorld()) continue;
                double d = mate.getLocation().distance(player.getLocation());
                if (d < best) { best = d; nearest = mate; }
            }
        }
        if (nearest == null) {
            player.sendMessage(plugin.messages().prefixed("compass.none", null));
            return;
        }
        player.setCompassTarget(nearest.getLocation());
        player.sendMessage(plugin.messages().prefixed("compass.ally", Map.of(
                "name", nearest.getName(), "distance", String.valueOf((int) best))));
    }

    private void pointToNearestEnemy(Player player, GameInstance game) {
        Team myTeam = game.teams().getTeam(player.getUniqueId());
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Team t : game.teams().aliveTeams()) {
            if (t == myTeam) continue;
            for (UUID id : t.getAlive()) {
                Player enemy = plugin.getServer().getPlayer(id);
                if (enemy == null || enemy.getWorld() != player.getWorld()) continue;
                double d = enemy.getLocation().distance(player.getLocation());
                if (d < best) { best = d; nearest = enemy; }
            }
        }
        if (nearest == null) {
            player.sendMessage(plugin.messages().prefixed("compass.none", null));
            return;
        }
        Location loc = nearest.getLocation();
        player.setCompassTarget(loc);
        // Nick przeciwnika ukryty (spec).
        player.sendMessage(plugin.messages().prefixed("compass.enemy", Map.of(
                "distance", String.valueOf((int) best))));
    }

    public void clear(UUID uuid) {
        modes.remove(uuid);
    }
}
