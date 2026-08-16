package pl.ultrahc.paper.game;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Zarzadza cyklem gier na tym arena-serwerze. Zgodnie z DECYZJA: 1 serwer = 1
 * aktywna gra, wiec trzymamy jedna biezaca instancje i po jej zakonczeniu
 * tworzymy nowa na swiezym swiecie z puli.
 */
public class GameManager {

    private final UltraHcPlugin plugin;
    private final WorldManager worldManager;
    private GameInstance current;

    public GameManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.worldManager = new WorldManager(plugin, plugin.configManager());
    }

    /** Start areny: przygotuj pule swiatow i pierwsza instancje. */
    public void enableArena() {
        worldManager.ensurePool();
        startNewInstance();
        autoJoinLobby(); // gracze juz obecni w poczekalni dolaczaja do WAITING
    }

    /** Dolacza do biezacej gry (WAITING) wszystkich graczy stojacych w poczekalni. */
    public void autoJoinLobby() {
        if (current == null || plugin.arenaLobby() == null) return;
        if (!plugin.configManager().raw().getBoolean("arena.lobby.auto-join", true)) return;
        for (Player p : plugin.arenaLobby().world().getPlayers()) {
            current.addPlayer(p);
        }
    }

    private void startNewInstance() {
        World world = worldManager.takeWorld();
        current = new GameInstance(plugin, world);
        plugin.getLogger().info("[UltraHC] Nowa instancja gry na swiecie " + world.getName() + " (stan WAITING).");
    }

    public GameInstance current() {
        return current;
    }

    public boolean join(Player player) {
        if (current == null) return false;
        return current.addPlayer(player);
    }

    public void leave(UUID uuid) {
        if (current != null) current.removePlayer(uuid);
    }

    /** Sprzatanie po zakonczonej grze + start kolejnej instancji (opoznione). */
    public void onInstanceEnded(GameInstance ended) {
        long delay = 20L * 10; // 10 s ekranu konca
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World oldWorld = ended.world();
            ended.shutdown();
            startNewInstance();
            // Przenies pozostalych graczy z powrotem do poczekalni, potem skasuj stary swiat.
            for (UUID id : new ArrayList<>(ended.participants())) {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null) continue;
                if (plugin.arenaLobby() != null) {
                    plugin.arenaLobby().send(p);
                } else {
                    p.teleport(current.world().getSpawnLocation());
                    p.setGameMode(GameMode.ADVENTURE);
                }
            }
            if (plugin.configManager().raw().getBoolean("world.delete-after-game", true)) {
                worldManager.deleteWorld(oldWorld);
            }
            autoJoinLobby(); // gracze w poczekalni dolaczaja do nowej instancji
        }, delay);
    }

    public WorldManager worlds() {
        return worldManager;
    }

    public void shutdown() {
        if (current != null) current.shutdown();
        worldManager.shutdown();
    }

    /** Lista opisu stanu (do komendy /uhc gameinfo). */
    public List<String> describeState() {
        List<String> out = new ArrayList<>();
        if (current == null) {
            out.add("Brak aktywnej instancji.");
            return out;
        }
        out.add("Stan: " + current.state());
        out.add("Swiat: " + current.world().getName());
        out.add("Uczestnicy: " + current.participants().size());
        if (current.teams() != null) {
            out.add("Zywi: " + current.teams().alivePlayers());
            out.add("Zywe druzyny: " + current.teams().aliveTeams().size());
        }
        out.add("Czas gry (s): " + current.elapsedSeconds());
        return out;
    }
}
