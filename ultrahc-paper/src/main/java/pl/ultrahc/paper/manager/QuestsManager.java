package pl.ultrahc.paper.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Questy (Krzysiu): dzienne / tygodniowe / stale. Postep per-gracz trzymany w DB
 * z kluczem okresu — dzienne resetuja sie co dzien, tygodniowe co tydzien (przez
 * porownanie zapisanego okresu z aktualnym), stale nigdy. Definicje w config.
 */
public class QuestsManager {

    public enum Type { DAILY, WEEKLY, PERMANENT }
    public enum Objective { KILLS, WINS, GAMES_PLAYED, PLAY_MINUTES }

    public record QuestDef(String id, Type type, Objective objective, long target,
                           String name, long rewardXp, long rewardPd) {}

    private final UltraHcPlugin plugin;
    private final ShopCurrencyManager currency;
    private final LevelsManager levels;

    private final Map<String, QuestDef> defs = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Storage.QuestRecord>> cache = new ConcurrentHashMap<>();

    public QuestsManager(UltraHcPlugin plugin, ShopCurrencyManager currency, LevelsManager levels) {
        this.plugin = plugin;
        this.currency = currency;
        this.levels = levels;
        loadDefs();
    }

    public void loadDefs() {
        defs.clear();
        ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("quests.definitions");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection q = sec.getConfigurationSection(id);
            if (q == null) continue;
            try {
                defs.put(id, new QuestDef(id,
                        Type.valueOf(q.getString("type", "PERMANENT").toUpperCase(Locale.ROOT)),
                        Objective.valueOf(q.getString("objective", "KILLS").toUpperCase(Locale.ROOT)),
                        q.getLong("target", 1),
                        q.getString("name", id),
                        q.getLong("reward-xp", 0),
                        q.getLong("reward-pd", 0)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[UltraHC] Bledna definicja questa " + id + ": " + e.getMessage());
            }
        }
    }

    public Map<String, QuestDef> defs() { return defs; }

    // ------------------------------------------------------------ cykl gracza
    public void loadAsync(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Storage.QuestRecord> map = new ConcurrentHashMap<>();
            try {
                for (Storage.QuestRecord r : plugin.profiles().storage().loadQuests(uuid)) {
                    map.put(r.questId(), r);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[UltraHC] Blad ladowania questow: " + e.getMessage());
            }
            cache.put(uuid, map);
        });
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    /** Aktualny (znormalizowany do biezacego okresu) rekord questa. */
    public Storage.QuestRecord record(UUID uuid, QuestDef def) {
        Map<String, Storage.QuestRecord> map = cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        String period = currentPeriod(def.type());
        Storage.QuestRecord rec = map.get(def.id());
        if (rec == null || !rec.period().equals(period)) {
            rec = new Storage.QuestRecord(def.id(), period, 0, false); // nowy okres = reset
            map.put(def.id(), rec);
        }
        return rec;
    }

    // ------------------------------------------------------------ postep
    /** Dodaje postep wszystkim questom o danym celu; przy ukonczeniu wyplaca nagrode. */
    public void increment(UUID uuid, Objective objective, long amount) {
        if (amount <= 0) return;
        for (QuestDef def : defs.values()) {
            if (def.objective() != objective) continue;
            Storage.QuestRecord rec = record(uuid, def);
            if (rec.completed()) continue;
            long progress = Math.min(def.target(), rec.progress() + amount);
            boolean completed = progress >= def.target();
            Storage.QuestRecord updated = new Storage.QuestRecord(def.id(), rec.period(), progress, completed);
            cache.get(uuid).put(def.id(), updated);
            persist(uuid, updated);
            if (completed) grantReward(uuid, def);
        }
    }

    private void grantReward(UUID uuid, QuestDef def) {
        PlayerProfile p = plugin.profiles().get(uuid);
        if (p != null) {
            currency.add(p, def.rewardXp());
            levels.addProgress(p, def.rewardPd());
            plugin.profiles().saveNow(p);
        }
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            online.sendMessage(plugin.messages().prefixed("quest.completed", Map.of(
                    "name", def.name(),
                    "xp", String.valueOf(def.rewardXp()),
                    "pd", String.valueOf(def.rewardPd()))));
        }
    }

    private void persist(UUID uuid, Storage.QuestRecord rec) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.profiles().storage().saveQuest(uuid, rec);
            } catch (Exception e) {
                plugin.getLogger().warning("[UltraHC] Blad zapisu questa: " + e.getMessage());
            }
        });
    }

    private String currentPeriod(Type type) {
        return switch (type) {
            case DAILY -> LocalDate.now().toString();
            case WEEKLY -> {
                LocalDate now = LocalDate.now();
                int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
                yield now.getYear() + "-W" + week;
            }
            case PERMANENT -> "PERM";
        };
    }
}
