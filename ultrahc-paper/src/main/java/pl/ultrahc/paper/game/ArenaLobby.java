package pl.ultrahc.paper.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Poczekalnia areny: TRWALY swiat (z hologramami/NPC/topkami), w ktorym gracze
 * czekaja przed startem meczu. Sam mecz rozgrywa sie w osobnym, kasowanym swiecie
 * (patrz {@link WorldManager}). Dzieki temu poczekalnia jest izolowana od mapy gry.
 */
public class ArenaLobby {

    private final UltraHcPlugin plugin;

    public ArenaLobby(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Swiat poczekalni: skonfigurowany (arena.lobby.world) albo glowny swiat serwera. */
    public World world() {
        String name = plugin.configManager().raw().getString("arena.lobby.world", "");
        if (name != null && !name.isBlank()) {
            World w = plugin.getServer().getWorld(name);
            if (w != null) return w;
            plugin.getLogger().warning("[UltraHC] Swiat poczekalni '" + name + "' nie zaladowany — uzywam glownego.");
        }
        return plugin.getServer().getWorlds().get(0);
    }

    /** Punkt spawnu poczekalni: skonfigurowany (arena.lobby.spawn) albo spawn swiata. */
    public Location spawn() {
        Location loc = plugin.readLoc("arena.lobby.spawn");
        return loc != null ? loc : world().getSpawnLocation();
    }

    /** Przenosi gracza do poczekalni w trybie przygotowania (z przedmiotem powrotu do huba). */
    public void send(Player player) {
        player.teleport(spawn());
        player.setGameMode(GameMode.ADVENTURE);
        if (plugin.arenaLobbyListener() != null) plugin.arenaLobbyListener().giveHubItem(player);
        pl.ultrahc.paper.util.Feedback.joinRing(player); // efekt wejscia do poczekalni
    }
}
