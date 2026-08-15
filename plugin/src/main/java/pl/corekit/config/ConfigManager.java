package pl.corekit.config;

import org.bukkit.configuration.file.FileConfiguration;
import pl.corekit.CoreKitPlugin;

/**
 * Loads and reloads {@code config.yml}, merging in any keys added by newer
 * plugin versions and exposing a typed {@link Settings} view.
 */
public final class ConfigManager {

    /**
     * Bump this whenever the config schema changes in a way that warrants
     * operator attention. Missing keys are back-filled automatically; this
     * number only drives a heads-up log line.
     */
    private static final int CURRENT_VERSION = 1;

    private final CoreKitPlugin plugin;
    private volatile Settings settings;

    public ConfigManager(CoreKitPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Back-fill keys introduced in newer versions from the bundled defaults.
        config.options().copyDefaults(true);
        plugin.saveConfig();

        int version = config.getInt("config-version", 1);
        if (version != CURRENT_VERSION) {
            plugin.getSLF4JLogger().warn(
                    "config.yml is version {} but this build expects {}. New options were "
                            + "added with defaults; review config.yml to opt into them.",
                    version, CURRENT_VERSION);
        }

        this.settings = Settings.from(config);
    }

    public Settings settings() {
        return settings;
    }
}
