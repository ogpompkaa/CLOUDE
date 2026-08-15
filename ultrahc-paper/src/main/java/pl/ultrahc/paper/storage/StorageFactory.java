package pl.ultrahc.paper.storage;

import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.common.storage.SqliteStorage;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.config.ConfigManager;

import java.io.File;

/** Tworzy implementacje {@link Storage} zgodnie z config (SQLITE teraz, MYSQL pozniej). */
public final class StorageFactory {

    private StorageFactory() {}

    public static Storage create(JavaPlugin plugin, ConfigManager config) {
        return switch (config.storageType()) {
            case SQLITE -> {
                File file = new File(plugin.getDataFolder(), config.sqliteFile());
                yield new SqliteStorage(file.getAbsolutePath(), plugin.getLogger());
            }
            case MYSQL -> throw new IllegalStateException(
                    "MysqlStorage zostanie dodany na etapie produkcyjnym — ustaw storage.type: SQLITE.");
        };
    }
}
