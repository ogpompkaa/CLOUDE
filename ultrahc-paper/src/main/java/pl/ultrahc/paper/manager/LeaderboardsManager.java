package pl.ultrahc.paper.manager;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Topki: Top 10 Zabojstw / Wygranych / Poziomow. Dane z DAO (odswiezane cyklicznie
 * asynchronicznie), prezentacja przez {@link pl.ultrahc.paper.hologram.HologramManager}.
 */
public class LeaderboardsManager {

    private final UltraHcPlugin plugin;
    private final Map<Storage.LeaderboardType, List<Storage.LeaderboardEntry>> cache = new EnumMap<>(Storage.LeaderboardType.class);
    private BukkitTask task;

    public LeaderboardsManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        int seconds = plugin.configManager().raw().getInt("lobby.leaderboards.refresh-seconds", 60);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, seconds * 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    /** Odswiezenie danych (async) + aktualizacja hologramow (na watku glownym). */
    public void refresh() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (Storage.LeaderboardType type : Storage.LeaderboardType.values()) {
                    cache.put(type, plugin.profiles().storage().topBy(type, 10));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[UltraHC] Blad odswiezania topek: " + e.getMessage());
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, this::updateHolograms);
        });
    }

    public List<Storage.LeaderboardEntry> top(Storage.LeaderboardType type) {
        return cache.getOrDefault(type, List.of());
    }

    /** Sformatowane linie hologramu dla danej kategorii. */
    public List<String> lines(Storage.LeaderboardType type) {
        var msg = plugin.messages();
        String titleKey = switch (type) {
            case KILLS -> "leaderboard.title-kills";
            case WINS -> "leaderboard.title-wins";
            case LEVEL -> "leaderboard.title-level";
        };
        List<String> lines = new ArrayList<>();
        lines.add(msg.raw(titleKey));
        List<Storage.LeaderboardEntry> entries = top(type);
        if (entries.isEmpty()) {
            lines.add(msg.raw("leaderboard.empty"));
        } else {
            int pos = 1;
            for (Storage.LeaderboardEntry e : entries) {
                lines.add(msg.raw("leaderboard.entry", Map.of(
                        "pos", String.valueOf(pos++), "name", e.name(), "value", String.valueOf(e.value()))));
            }
        }
        return lines;
    }

    private void updateHolograms() {
        if (plugin.holograms() == null || !plugin.holograms().available()) return;
        ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("lobby.leaderboards.holograms");
        if (sec == null) return;
        updateOne(sec, "kills", Storage.LeaderboardType.KILLS);
        updateOne(sec, "wins", Storage.LeaderboardType.WINS);
        updateOne(sec, "level", Storage.LeaderboardType.LEVEL);
    }

    private void updateOne(ConfigurationSection sec, String key, Storage.LeaderboardType type) {
        Location loc = readLocation(sec.getConfigurationSection(key));
        if (loc == null) return;
        plugin.holograms().createOrUpdate("uhc_top_" + key, loc, lines(type));
    }

    private Location readLocation(ConfigurationSection s) {
        if (s == null || !s.contains("world")) return null;
        var world = plugin.getServer().getWorld(s.getString("world"));
        if (world == null) return null;
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
    }
}
