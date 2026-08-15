package pl.ultrahc.paper.manager;

import org.bukkit.configuration.ConfigurationSection;
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

    private String mode;
    private double slope, intercept;      // LINEAR
    private double geomBase, geomGrowth;  // GEOMETRIC
    private final Map<Integer, Long> overrides = new HashMap<>();
    private String starSymbol;

    public LevelsManager(ConfigManager config) {
        this.config = config;
        reload();
    }

    public void reload() {
        var c = config.raw();
        this.mode = c.getString("levels.mode", "LINEAR").toUpperCase();
        this.slope = c.getDouble("levels.linear.slope", 2000);
        this.intercept = c.getDouble("levels.linear.intercept", 500);
        this.geomBase = c.getDouble("levels.geometric.base", 750);
        this.geomGrowth = c.getDouble("levels.geometric.growth", 1.35);
        this.starSymbol = c.getString("levels.star-symbol", "gwiazdka");
        overrides.clear();
        ConfigurationSection ov = c.getConfigurationSection("levels.overrides");
        if (ov != null) {
            for (String key : ov.getKeys(false)) {
                try {
                    overrides.put(Integer.parseInt(key), ov.getLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    /** Ile PD potrzeba, aby awansowac Z podanego poziomu na nastepny. */
    public long requiredForLevel(int level) {
        Long override = overrides.get(level);
        if (override != null) return override;
        if ("GEOMETRIC".equals(mode)) {
            return Math.round(geomBase * Math.pow(geomGrowth, level));
        }
        return Math.round(slope * level + intercept);
    }

    /**
     * Dodaje PD do profilu, obslugujac wielokrotne awanse.
     * @return liczba zdobytych poziomow (0 gdy brak awansu)
     */
    public int addProgress(PlayerProfile profile, long amount) {
        long progress = profile.getProgressPoints() + Math.max(0, amount);
        int levelsGained = 0;
        long required = requiredForLevel(profile.getLevel());
        while (progress >= required) {
            progress -= required;
            profile.setLevel(profile.getLevel() + 1);
            levelsGained++;
            required = requiredForLevel(profile.getLevel());
        }
        profile.setProgressPoints(progress);
        return levelsGained;
    }

    /** Etykieta poziomu, np. "3 gwiazdka". */
    public String starLabel(PlayerProfile profile) {
        return profile.getLevel() + " " + starSymbol;
    }

    public String starSymbol() {
        return starSymbol;
    }
}
