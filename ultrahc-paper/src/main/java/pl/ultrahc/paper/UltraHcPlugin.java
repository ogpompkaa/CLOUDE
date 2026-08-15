package pl.ultrahc.paper;

import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.command.UhcCommand;
import pl.ultrahc.paper.config.ConfigManager;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.listener.ProfileListener;
import pl.ultrahc.paper.manager.LevelsManager;
import pl.ultrahc.paper.manager.ShopCurrencyManager;
import pl.ultrahc.paper.profile.ProfileService;
import pl.ultrahc.paper.storage.StorageFactory;

/**
 * Punkt wejscia pluginu UltraHC. Wg {@code server.role} wstaja rozne managery
 * (LOBBY vs ARENA). Na tym etapie (szkielet) uruchamiamy warstwe wspolna:
 * config, messages, magazyn danych, profile oraz fundament walut (XP + PD).
 */
public class UltraHcPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private ServerRole role;

    private Storage storage;
    private ProfileService profileService;
    private ShopCurrencyManager currencyManager;
    private LevelsManager levelsManager;

    @Override
    public void onEnable() {
        // 1. Konfiguracja i komunikaty
        this.configManager = new ConfigManager(this);
        configManager.load();
        this.messagesManager = new MessagesManager(this);
        messagesManager.load();
        this.role = configManager.role();

        // 2. Magazyn danych (DAO)
        this.storage = StorageFactory.create(this, configManager);
        try {
            storage.init();
        } catch (Exception e) {
            getLogger().severe("[UltraHC] Nie udalo sie zainicjalizowac magazynu: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Profile + fundament walut (trzy odrebne systemy: XP / PD / natywny exp)
        this.profileService = new ProfileService(this, storage);
        this.currencyManager = new ShopCurrencyManager();
        this.levelsManager = new LevelsManager(configManager);

        // 4. Eventy i komendy
        getServer().getPluginManager().registerEvents(new ProfileListener(profileService), this);
        var cmd = getCommand("uhc");
        if (cmd != null) {
            cmd.setExecutor(new UhcCommand(this));
        }

        getLogger().info("[UltraHC] Wlaczono. Rola serwera: " + role + ", magazyn: " + configManager.storageType() + ".");
        // TODO(kolejne etapy): wg role uruchom GameManager/BorderManager (ARENA)
        //                      albo NpcManager/ShopManager/Leaderboards (LOBBY).
    }

    @Override
    public void onDisable() {
        if (profileService != null) profileService.saveAll();
        if (storage != null) storage.close();
        getLogger().info("[UltraHC] Wylaczono. Profile zapisane.");
    }

    public ConfigManager configManager() { return configManager; }
    public MessagesManager messages() { return messagesManager; }
    public ServerRole role() { return role; }
    public ProfileService profiles() { return profileService; }
    public ShopCurrencyManager currency() { return currencyManager; }
    public LevelsManager levels() { return levelsManager; }
}
