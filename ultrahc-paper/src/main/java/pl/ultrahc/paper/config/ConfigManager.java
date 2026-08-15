package pl.ultrahc.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.common.storage.StorageType;
import pl.ultrahc.paper.ServerRole;

/**
 * Dostep do config.yml. Cały balans jest tutaj — managery czytaja wartosci stąd,
 * nigdy nie hardkoduja. Cienka warstwa nad FileConfiguration Bukkita.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return cfg;
    }

    public ServerRole role() {
        return ServerRole.fromString(cfg.getString("server.role", "LOBBY"));
    }

    public StorageType storageType() {
        String t = cfg.getString("storage.type", "SQLITE");
        try {
            return StorageType.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[UltraHC] Nieznany storage.type='" + t + "', uzywam SQLITE.");
            return StorageType.SQLITE;
        }
    }

    public String sqliteFile() {
        return cfg.getString("storage.sqlite.file", "ultrahc.db");
    }
}
