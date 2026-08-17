package pl.ultrahc.paper.profile;

import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.common.storage.Storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pamiec podreczna profili graczy online + zapis/odczyt przez warstwe DAO.
 * Zapisuje profil tylko serwer, na ktorym gracz jest aktualnie (brak kontencji
 * w sieci — patrz DECYZJE, sekcja 1).
 */
public class ProfileService {

    private final JavaPlugin plugin;
    private final Storage storage;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public ProfileService(JavaPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /** Wczytuje profil asynchronicznie (przy wejsciu gracza) i wrzuca do cache. */
    public void loadAsync(UUID uuid, String name) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerProfile profile = storage.loadProfile(uuid, name);
                cache.put(uuid, profile);
            } catch (Exception e) {
                plugin.getLogger().severe("[UltraHC] Blad ladowania profilu " + name + ": " + e.getMessage());
            }
        });
    }

    /** Profil z cache (null gdy jeszcze niezaladowany). */
    public PlayerProfile get(UUID uuid) {
        return cache.get(uuid);
    }

    /** Zapisuje asynchronicznie i usuwa z cache (przy wyjsciu gracza). */
    public void saveAndUnloadAsync(UUID uuid) {
        PlayerProfile profile = cache.remove(uuid);
        if (profile == null) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> saveNow(profile));
    }

    /** Natychmiastowy zapis (uzywany m.in. przy zamykaniu pluginu — juz w watku glownym). */
    public void saveNow(PlayerProfile profile) {
        try {
            storage.saveProfile(profile);
        } catch (Exception e) {
            plugin.getLogger().severe("[UltraHC] Blad zapisu profilu " + profile.getName() + ": " + e.getMessage());
        }
    }

    /** Zapisuje wszystkie profile w cache (np. onDisable). */
    public void saveAll() {
        for (PlayerProfile p : cache.values()) {
            saveNow(p);
        }
    }

    public Storage storage() {
        return storage;
    }
}
