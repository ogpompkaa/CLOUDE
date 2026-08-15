package pl.corekit;

import org.bukkit.plugin.java.JavaPlugin;
import pl.corekit.command.CommandRegistrar;
import pl.corekit.config.ConfigManager;
import pl.corekit.lang.MessageService;
import pl.corekit.listener.PlayerConnectionListener;
import pl.corekit.storage.DatabaseManager;
import pl.corekit.storage.PlayerProfileRepository;

/**
 * Entry point of the CoreKit foundation.
 *
 * <p>The plugin follows a strict start-up order: configuration is loaded first,
 * then localisation, then storage. Storage failure is treated as fatal — a
 * foundation that silently runs without its database would corrupt every
 * feature built on top of it, so we disable the plugin instead.
 *
 * <p>All heavy I/O (database access) happens off the main server thread; this
 * class only wires the services together and manages their lifecycle.
 */
public final class CoreKitPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messages;
    private DatabaseManager database;
    private PlayerProfileRepository profiles;

    @Override
    public void onEnable() {
        // 1. Configuration.
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        // 2. Localisation (depends on the configured language).
        this.messages = new MessageService(this, configManager);
        this.messages.load();

        // 3. Storage. Fatal if it cannot be initialised.
        this.database = new DatabaseManager(this, configManager.settings());
        try {
            this.database.initialize();
        } catch (Exception ex) {
            getSLF4JLogger().error("Failed to initialise storage — disabling CoreKit.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.profiles = new PlayerProfileRepository(database);

        // 4. Wiring: listeners and commands.
        getServer().getPluginManager()
                .registerEvents(new PlayerConnectionListener(this, profiles, messages), this);
        new CommandRegistrar(this, messages).register();

        getSLF4JLogger().info("CoreKit v{} enabled.", getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        // Guard against a failed onEnable: any of these may be null.
        if (database != null) {
            database.shutdown();
        }
        getSLF4JLogger().info("CoreKit disabled.");
    }

    /**
     * Reloads configuration and localisation at runtime. Storage settings that
     * require a reconnect (pool size, backend) are intentionally NOT re-applied
     * here — changing them safely requires draining in-flight queries, which is
     * a restart-level operation. The command surfaces that caveat to the user.
     */
    public void reload() {
        configManager.load();
        messages.load();
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public MessageService messages() {
        return messages;
    }

    public DatabaseManager database() {
        return database;
    }

    public PlayerProfileRepository profiles() {
        return profiles;
    }
}
