package pl.corekit.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable, typed snapshot of {@code config.yml}. Reading configuration keys
 * as raw strings all over the codebase invites typos and default-value drift;
 * parsing them once into this record gives the rest of the plugin a clean,
 * misuse-resistant API and a single place to evolve the schema.
 */
public record Settings(
        String language,
        boolean debug,
        String storageFile,
        int poolSize,
        int defaultHomeLimit,
        int warmupSeconds,
        int cooldownSeconds,
        boolean cancelOnMove,
        boolean cancelOnDamage,
        boolean soundsEnabled,
        boolean actionBarEnabled
) {

    static Settings from(FileConfiguration config) {
        return new Settings(
                config.getString("settings.language", "en"),
                config.getBoolean("settings.debug", false),
                config.getString("storage.file", "data.db"),
                Math.max(1, config.getInt("storage.pool-size", 4)),
                Math.max(0, config.getInt("homes.default-limit", 1)),
                Math.max(0, config.getInt("teleport.warmup-seconds", 3)),
                Math.max(0, config.getInt("teleport.cooldown-seconds", 5)),
                config.getBoolean("teleport.cancel-on-move", true),
                config.getBoolean("teleport.cancel-on-damage", true),
                config.getBoolean("feedback.sounds", true),
                config.getBoolean("feedback.action-bar", true)
        );
    }
}
