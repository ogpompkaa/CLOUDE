package pl.ultrahc.paper.manager;

import org.bukkit.configuration.ConfigurationSection;
import pl.ultrahc.common.game.LevelCurve;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;

/**
 * System PD i poziomow "gwiazdki".
 *
 * <p>SYSTEM ODRĘBNY od waluty sklepowej ({@link ShopCurrencyManager}) i natywnego
 * expa MC. Prog na poziom liczy sie z tablicy override (poziomy 0-2 ze spec) lub
 * ze wzoru parametryzowanego (LINEAR/GEOMETRIC) — patrz DECYZJE, sekcja 3.
 */
public class LevelsManager {

    private final ConfigManager config;

    private LevelCurve curve;
    private String starSymbol;

    public LevelsManager(ConfigManager config) {
        this.config = config;
        reload();
    }

    public void reload() {
        var c = config.raw();
        LevelCurve.Mode mode = "GEOMETRIC".equalsIgnoreCase(c.getString("levels.mode", "LINEAR"))
                ? LevelCurve.Mode.GEOMETRIC : LevelCurve.Mode.LINEAR;
        Map<Integer, Long> overrides = new HashMap<>();
        ConfigurationSection ov = c.getConfigurationSection("levels.overrides");
        if (ov != null) {
            for (String key : ov.getKeys(false)) {
                try {
                    overrides.put(Integer.parseInt(key), ov.getLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        this.curve = new LevelCurve(mode,
                c.getDouble("levels.linear.slope", 2000),
                c.getDouble("levels.linear.intercept", 500),
                c.getDouble("levels.geometric.base", 750),
                c.getDouble("levels.geometric.growth", 1.35),
                overrides);
        this.starSymbol = c.getString("levels.star-symbol", "gwiazdka");
    }

    /** Ile PD potrzeba, aby awansowac Z podanego poziomu na nastepny. */
    public long requiredForLevel(int level) {
        return curve.requiredForLevel(level);
    }

    /**
     * Dodaje PD do profilu, obslugujac wielokrotne awanse.
     * @return liczba zdobytych poziomow (0 gdy brak awansu)
     */
    public int addProgress(PlayerProfile profile, long amount) {
        LevelCurve.Progress result = curve.applyProgress(profile.getLevel(), profile.getProgressPoints(), amount);
        profile.setLevel(result.level());
        profile.setProgressPoints(result.remaining());
        return result.gained();
    }

    /** Etykieta poziomu, np. "3 gwiazdka". */
    public String starLabel(PlayerProfile profile) {
        return profile.getLevel() + " " + starSymbol;
    }

    public String starSymbol() {
        return starSymbol;
    }
}
