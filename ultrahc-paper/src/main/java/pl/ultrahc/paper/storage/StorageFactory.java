package pl.ultrahc.paper.storage;

import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.common.storage.MysqlStorage;
import pl.ultrahc.common.storage.SqliteStorage;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.config.ConfigManager;

import java.io.File;

/** Tworzy implementacje {@link Storage} zgodnie z config (SQLITE dev / MYSQL produkcja). */
public final class StorageFactory {

    private StorageFactory() {}

    public static Storage create(JavaPlugin plugin, ConfigManager config) {
        return switch (config.storageType()) {
            case SQLITE -> {
                File file = new File(plugin.getDataFolder(), config.sqliteFile());
                yield new SqliteStorage(file.getAbsolutePath(), plugin.getLogger());
            }
            case MYSQL -> {
                var c = config.raw();
                yield new MysqlStorage(
                        c.getString("storage.mysql.host", "127.0.0.1"),
                        c.getInt("storage.mysql.port", 3306),
                        c.getString("storage.mysql.database", "ultrahc"),
                        c.getString("storage.mysql.user", "ultrahc"),
                        c.getString("storage.mysql.password", ""),
                        c.getInt("storage.mysql.pool-size", 10),
                        plugin.getLogger());
            }
        };
    }
}
