package pl.ultrahc.paper.manager;

import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.common.instances.InstanceInfo;
import pl.ultrahc.common.instances.InstanceRegistry;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;

import java.io.File;
import java.util.List;

/**
 * Wpisuje/odczytuje stan instancji w rejestrze sieciowym. ARENA odswieza swoj
 * wiersz (heartbeat), LOBBY czyta liste dolaczalnych gier do matchmakingu.
 * Izolacja per-instancja: kazdy arena-serwer trzyma tylko swoja gre (1:1).
 */
public class InstanceManager {

    private final UltraHcPlugin plugin;
    private InstanceRegistry registry;
    private String instanceId;
    private BukkitTask heartbeatTask;

    public InstanceManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Buduje rejestr z tej samej konfiguracji co magazyn (SQLite dev / MySQL prod). */
    private InstanceRegistry buildRegistry() {
        var cfg = plugin.configManager();
        return switch (cfg.storageType()) {
            case SQLITE -> {
                File f = new File(plugin.getDataFolder(), cfg.sqliteFile());
                yield new InstanceRegistry("jdbc:sqlite:" + f.getAbsolutePath(), null, null, plugin.getLogger());
            }
            case MYSQL -> {
                var c = cfg.raw();
                String url = "jdbc:mysql://" + c.getString("storage.mysql.host", "127.0.0.1") + ":"
                        + c.getInt("storage.mysql.port", 3306) + "/" + c.getString("storage.mysql.database", "ultrahc");
                yield new InstanceRegistry(url, c.getString("storage.mysql.user"),
                        c.getString("storage.mysql.password"), plugin.getLogger());
            }
        };
    }

    private boolean initRegistry() {
        this.registry = buildRegistry();
        try {
            registry.init();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[UltraHC] Nie udalo sie zainicjalizowac rejestru instancji: " + e.getMessage());
            registry = null;
            return false;
        }
    }

    /** ARENA: rejestracja + cykliczny heartbeat stanu gry. */
    public void startArena() {
        if (!initRegistry()) return;
        this.instanceId = resolveInstanceId();
        int period = Math.max(1, plugin.configManager().raw().getInt("network.heartbeat-seconds", 3));
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::heartbeat, 20L, period * 20L);
        plugin.getLogger().info("[UltraHC] Instancja zarejestrowana jako '" + instanceId + "'.");
    }

    /** LOBBY: tylko odczyt rejestru (matchmaking). */
    public void startLobby() {
        initRegistry();
    }

    private void heartbeat() {
        if (registry == null) return;
        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        int teamSize = plugin.configManager().raw().getInt("game.team-size", 1);
        int maxPlayers = plugin.configManager().raw().getInt("game.max-players", 100);
        String state = game == null ? "WAITING" : game.state().name();
        int players = game == null ? 0 : game.participants().size();
        try {
            // Zdalne zadanie zamkniecia (np. z panelu admina w lobby) -> zakoncz gre.
            if (registry.consumeCloseRequest(instanceId) && game != null) {
                game.forceEnd();
            }
            registry.upsert(new InstanceInfo(instanceId, state, players, maxPlayers, teamSize,
                    modeName(teamSize), System.currentTimeMillis()));
        } catch (Exception e) {
            plugin.getLogger().warning("[UltraHC] Blad heartbeatu instancji: " + e.getMessage());
        }
    }

    /** Lista wszystkich zarejestrowanych instancji (panel admina). */
    public List<InstanceInfo> listAll() {
        if (registry == null) return List.of();
        try {
            return registry.listAll();
        } catch (Exception e) {
            plugin.getLogger().warning("[UltraHC] Blad listy instancji: " + e.getMessage());
            return List.of();
        }
    }

    /** Zadanie zamkniecia instancji (arena odbierze przy heartbeacie). */
    public void requestClose(String id) {
        if (registry == null) return;
        try {
            registry.requestClose(id);
        } catch (Exception e) {
            plugin.getLogger().warning("[UltraHC] Blad zadania zamkniecia " + id + ": " + e.getMessage());
        }
    }

    /** LOBBY: lista dolaczalnych instancji dla danego rozmiaru druzyny. */
    public List<InstanceInfo> joinable(int teamSize) {
        if (registry == null) return List.of();
        long stale = plugin.configManager().raw().getInt("network.instance-stale-seconds", 10) * 1000L;
        try {
            return registry.listJoinable(teamSize, stale);
        } catch (Exception e) {
            plugin.getLogger().warning("[UltraHC] Blad odczytu rejestru: " + e.getMessage());
            return List.of();
        }
    }

    private String resolveInstanceId() {
        String configured = plugin.configManager().raw().getString("server.instance-name", "");
        if (configured != null && !configured.isBlank()) return configured;
        // Fallback: port serwera (musi pasowac do nazwy serwera w Velocity).
        return "arena-" + plugin.getServer().getPort();
    }

    public static String modeName(int teamSize) {
        return switch (teamSize) {
            case 2 -> "DUO";
            case 3 -> "TRIO";
            case 4 -> "SQUAD";
            default -> "SOLO";
        };
    }

    public void shutdown() {
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (registry != null) {
            try {
                if (instanceId != null) registry.remove(instanceId);
            } catch (Exception ignored) {
            }
            registry.close();
        }
    }
}
