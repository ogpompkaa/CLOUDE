package pl.ultrahc.paper;

import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.command.UhcCommand;
import pl.ultrahc.paper.config.ConfigManager;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.game.GameManager;
import pl.ultrahc.paper.listener.ClassAbilityListener;
import pl.ultrahc.paper.listener.CombatListener;
import pl.ultrahc.paper.listener.CompassListener;
import pl.ultrahc.paper.listener.DropsListener;
import pl.ultrahc.paper.listener.DetectorListener;
import pl.ultrahc.paper.listener.HeadListener;
import pl.ultrahc.paper.listener.PandoraListener;
import pl.ultrahc.paper.listener.ProfileListener;
import pl.ultrahc.paper.gui.ShopGui;
import pl.ultrahc.paper.hologram.DecentHologramsManager;
import pl.ultrahc.paper.hologram.HologramManager;
import pl.ultrahc.paper.npc.CitizensNpcManager;
import pl.ultrahc.paper.npc.NpcManager;
import pl.ultrahc.paper.manager.AbilityScheduler;
import pl.ultrahc.paper.manager.LeaderboardsManager;
import pl.ultrahc.paper.manager.BorderManager;
import pl.ultrahc.paper.manager.ClassesManager;
import pl.ultrahc.paper.manager.CompassManager;
import pl.ultrahc.paper.manager.HeadManager;
import pl.ultrahc.paper.manager.LevelsManager;
import pl.ultrahc.paper.manager.RecipeManager;
import pl.ultrahc.paper.manager.RewardManager;
import pl.ultrahc.paper.manager.ScoreboardService;
import pl.ultrahc.paper.manager.ShopCurrencyManager;
import pl.ultrahc.paper.manager.ShopManager;
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
    private GameManager gameManager;
    private BorderManager borderManager;
    private ScoreboardService scoreboardService;
    private RewardManager rewardManager;
    private HeadManager headManager;
    private CompassManager compassManager;
    private ClassesManager classesManager;
    private AbilityScheduler abilityScheduler;
    private ShopManager shopManager;
    private ShopGui shopGui;
    private RecipeManager recipeManager;
    private HologramManager hologramManager;
    private NpcManager npcManager;
    private LeaderboardsManager leaderboardsManager;

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
        this.classesManager = new ClassesManager(this, currencyManager); // buy/select w LOBBY, kit w ARENA
        this.shopManager = new ShopManager(this, currencyManager);        // kupno receptur (obie role)
        this.shopGui = new ShopGui(this);
        getServer().getPluginManager().registerEvents(shopGui, this);

        // 4. Eventy i komendy
        getServer().getPluginManager().registerEvents(new ProfileListener(profileService), this);
        var cmd = getCommand("uhc");
        if (cmd != null) {
            cmd.setExecutor(new UhcCommand(this));
        }

        // 5. Managery zalezne od roli
        if (role == ServerRole.ARENA) {
            this.borderManager = new BorderManager(this);
            this.rewardManager = new RewardManager(this, currencyManager, levelsManager);
            this.headManager = new HeadManager(this);
            this.compassManager = new CompassManager(this);
            this.gameManager = new GameManager(this);
            this.scoreboardService = new ScoreboardService(this);
            this.abilityScheduler = new AbilityScheduler(this);
            this.recipeManager = new RecipeManager(this);

            var pm = getServer().getPluginManager();
            pm.registerEvents(new DropsListener(this), this);
            pm.registerEvents(new CombatListener(this), this);
            pm.registerEvents(new HeadListener(this), this);
            pm.registerEvents(new CompassListener(this), this);
            pm.registerEvents(new ClassAbilityListener(this), this);
            pm.registerEvents(recipeManager, this);
            pm.registerEvents(new DetectorListener(this), this);
            pm.registerEvents(new PandoraListener(this), this);

            // Przygotowanie swiata blokuje watek glowny — robimy to po pelnym starcie serwera.
            getServer().getScheduler().runTask(this, () -> {
                recipeManager.registerAll();
                gameManager.enableArena();
                scoreboardService.start();
                abilityScheduler.start();
            });
        }

        if (role == ServerRole.LOBBY) {
            this.hologramManager = new DecentHologramsManager(this);
            this.npcManager = new CitizensNpcManager(this);
            this.leaderboardsManager = new LeaderboardsManager(this);
            // Po pelnym starcie: odswiez topki i postaw NPC (swiat lobby musi byc zaladowany).
            getServer().getScheduler().runTask(this, () -> {
                leaderboardsManager.start();
                spawnLobbyNpcs();
            });
        }

        getLogger().info("[UltraHC] Wlaczono. Rola serwera: " + role + ", magazyn: " + configManager.storageType() + ".");
        // TODO(kolejne etapy): LOBBY -> NpcManager/ShopManager/Leaderboards; ARENA -> Border/Scoreboard/Drops.
    }

    /** Stawia NPC lobby (Mietek/Krzysiu/Sklepikarz) wg pozycji z config. */
    private void spawnLobbyNpcs() {
        if (npcManager == null || !npcManager.available()) return;
        npcManager.removeAll();
        spawnNpc("mietek", "Mietek",
                p -> messagesManager.rawList("npc.mietek-info").forEach(l -> p.sendMessage(messagesManager.legacy(l))));
        spawnNpc("krzysiu", "Krzysiu",
                p -> p.sendMessage(messagesManager.legacy(messagesManager.raw("npc.krzysiu-info"))));
        spawnNpc("sklepikarz", "Sklepikarz", p -> shopGui.open(p));
    }

    /** Publiczne przeladowanie NPC lobby (po zmianie pozycji przez admina). */
    public void reloadLobbyNpcs() {
        spawnLobbyNpcs();
    }

    private void spawnNpc(String id, String name, java.util.function.Consumer<org.bukkit.entity.Player> action) {
        org.bukkit.Location loc = readLoc("lobby.npcs." + id);
        if (loc == null) {
            getLogger().info("[UltraHC] NPC " + id + " bez pozycji — ustaw /uhc setnpc " + id + ".");
            return;
        }
        npcManager.spawn(id, name, loc, action);
    }

    /** Odczyt lokalizacji z config (world,x,y,z[,yaw,pitch]); null gdy nieustawiona. */
    public org.bukkit.Location readLoc(String path) {
        var s = configManager.raw().getConfigurationSection(path);
        if (s == null || !s.contains("world")) return null;
        var world = getServer().getWorld(s.getString("world"));
        if (world == null) return null;
        return new org.bukkit.Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0));
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.removeAll();
        if (npcManager != null) npcManager.removeAll();
        if (leaderboardsManager != null) leaderboardsManager.stop();
        if (abilityScheduler != null) abilityScheduler.stop();
        if (scoreboardService != null) scoreboardService.stop();
        if (gameManager != null) gameManager.shutdown();
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
    public GameManager games() { return gameManager; }
    public BorderManager border() { return borderManager; }
    public RewardManager rewards() { return rewardManager; }
    public HeadManager heads() { return headManager; }
    public CompassManager compass() { return compassManager; }
    public ClassesManager classes() { return classesManager; }
    public ShopManager shop() { return shopManager; }
    public ShopGui shopGui() { return shopGui; }
    public RecipeManager recipes() { return recipeManager; }
    public HologramManager holograms() { return hologramManager; }
    public NpcManager npcs() { return npcManager; }
    public LeaderboardsManager leaderboards() { return leaderboardsManager; }
}
