package pl.ultrahc.paper.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.manager.QuestsManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nagrody XP (waluta sklepowa) i PD wg spec: za czas gry (progi rosnace),
 * zabojstwa i wygrana. Ulamkowe stawki czasowe (np. 4.2 XP) sa akumulowane
 * per-gracz i przenoszone do profilu calymi jednostkami (brak utraty ulamkow
 * w obrebie gry). Wartosci w config (rewards.*).
 */
public class RewardManager {

    private final UltraHcPlugin plugin;
    private final ShopCurrencyManager currency;
    private final LevelsManager levels;

    // Akumulatory ulamkowe per-gracz: [0]=XP, [1]=PD.
    private final Map<UUID, double[]> accum = new ConcurrentHashMap<>();
    private BukkitTask xpTask, pdTask;

    public RewardManager(UltraHcPlugin plugin, ShopCurrencyManager currency, LevelsManager levels) {
        this.plugin = plugin;
        this.currency = currency;
        this.levels = levels;
    }

    private FileConfiguration cfg() { return plugin.configManager().raw(); }

    // --------------------------------------------------------------- zabojstwo
    public void grantKill(UUID killer) {
        PlayerProfile p = plugin.profiles().get(killer);
        if (p == null) return;
        long xp = cfg().getLong("rewards.currency.per-kill", 100);
        long pd = cfg().getLong("rewards.progress.per-kill", 30);
        currency.add(p, xp);
        int gained = levels.addProgress(p, pd);
        p.setKills(p.getKills() + 1);
        plugin.profiles().saveNow(p);
        notifyLevel(killer, gained);
        if (plugin.quests() != null) plugin.quests().increment(killer, QuestsManager.Objective.KILLS, 1);
        // Popup nagrody na srodku ekranu.
        Player online = plugin.getServer().getPlayer(killer);
        if (online != null) {
            pl.ultrahc.paper.util.Feedback.title(online,
                    plugin.messages().component("title.kill-reward-main", Map.of("xp", String.valueOf(xp))),
                    plugin.messages().component("title.kill-reward-sub", Map.of("pd", String.valueOf(pd))));
        }
    }

    // ----------------------------------------------------------------- wygrana
    public void grantWin(Iterable<UUID> winners) {
        for (UUID id : winners) {
            PlayerProfile p = plugin.profiles().get(id);
            if (p == null) continue;
            currency.add(p, cfg().getLong("rewards.currency.per-win", 1000));
            int gained = levels.addProgress(p, cfg().getLong("rewards.progress.per-win", 250));
            p.setWins(p.getWins() + 1);
            plugin.profiles().saveNow(p);
            notifyLevel(id, gained);
            if (plugin.quests() != null) plugin.quests().increment(id, QuestsManager.Objective.WINS, 1);
        }
    }

    // ------------------------------------------------------- naliczanie czasowe
    public void startAccrual(GameInstance game) {
        stopAccrual();
        accum.clear();
        scheduleXp(game);
        schedulePd(game);
    }

    public void stopAccrual() {
        if (xpTask != null) { xpTask.cancel(); xpTask = null; }
        if (pdTask != null) { pdTask.cancel(); pdTask = null; }
    }

    /** Zapisuje profile uczestnikow (flush po grze). */
    public void saveParticipants(Iterable<UUID> participants) {
        for (UUID id : participants) {
            PlayerProfile p = plugin.profiles().get(id);
            if (p != null) plugin.profiles().saveNow(p);
        }
    }

    private void scheduleXp(GameInstance game) {
        int min = cfg().getInt("rewards.currency.time.interval-min-min", 1);
        int max = cfg().getInt("rewards.currency.time.interval-min-max", 2);
        long delayTicks = 20L * 60L * ThreadLocalRandom.current().nextInt(Math.max(1, min), Math.max(min, max) + 1);
        xpTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            awardTime(game, "rewards.currency.time.tiers", true);
            scheduleXp(game); // kolejny cykl z nowym losowym interwalem 1-2 min
        }, delayTicks);
    }

    private void schedulePd(GameInstance game) {
        int interval = cfg().getInt("rewards.progress.time.interval-min", 2);
        long delayTicks = 20L * 60L * Math.max(1, interval);
        pdTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                awardTime(game, "rewards.progress.time.tiers", false), delayTicks, delayTicks);
    }

    /** Przyznaje stawke z aktualnego progu wszystkim zywym uczestnikom. */
    private void awardTime(GameInstance game, String tiersPath, boolean isCurrency) {
        if (game.teams() == null) return;
        int minutes = (int) (game.elapsedSeconds() / 60);
        double amount = tierAmount(tiersPath, minutes);
        if (amount <= 0) return;
        long playMinutes = cfg().getInt("rewards.progress.time.interval-min", 2);
        for (UUID id : game.participants()) {
            if (!isAlive(game, id)) continue;
            accumulate(id, isCurrency ? amount : 0, isCurrency ? 0 : amount);
            // Postep questa czasowego licz w takcie PD (co interval-min minut).
            if (!isCurrency && plugin.quests() != null) {
                plugin.quests().increment(id, QuestsManager.Objective.PLAY_MINUTES, playMinutes);
            }
        }
    }

    private boolean isAlive(GameInstance game, UUID id) {
        var team = game.teams().getTeam(id);
        return team != null && team.isAlive(id);
    }

    private void accumulate(UUID id, double xp, double pd) {
        double[] a = accum.computeIfAbsent(id, k -> new double[2]);
        a[0] += xp;
        a[1] += pd;
        PlayerProfile p = plugin.profiles().get(id);
        if (p == null) return;
        long wholeXp = (long) a[0];
        if (wholeXp > 0) { currency.add(p, wholeXp); a[0] -= wholeXp; }
        long wholePd = (long) a[1];
        if (wholePd > 0) {
            int gained = levels.addProgress(p, wholePd);
            a[1] -= wholePd;
            notifyLevel(id, gained);
        }
    }

    /** Stawka z progu — delegacja do czystej (testowanej) logiki RewardTiers. */
    private double tierAmount(String path, int minutes) {
        List<pl.ultrahc.common.game.RewardTiers.Tier> tiers = new java.util.ArrayList<>();
        for (Map<?, ?> tier : cfg().getMapList(path)) {
            tiers.add(new pl.ultrahc.common.game.RewardTiers.Tier(
                    ((Number) tier.get("to-min")).intValue(),
                    ((Number) tier.get("amount")).doubleValue()));
        }
        return pl.ultrahc.common.game.RewardTiers.amountFor(tiers, minutes);
    }

    private void notifyLevel(UUID id, int levelsGained) {
        if (levelsGained <= 0) return;
        Player p = plugin.getServer().getPlayer(id);
        if (p == null) return;
        PlayerProfile profile = plugin.profiles().get(id);
        if (profile == null) return;
        p.sendMessage(plugin.messages().prefixed("progress.level-up", Map.of(
                "level", String.valueOf(profile.getLevel()),
                "star", plugin.levels().starSymbol())));
        var msg = plugin.messages();
        pl.ultrahc.paper.util.Feedback.title(p,
                msg.component("title.levelup-main", null),
                msg.component("title.levelup-sub", Map.of(
                        "level", String.valueOf(profile.getLevel()), "star", plugin.levels().starSymbol())));
        pl.ultrahc.paper.util.Feedback.levelUp(p);
        pl.ultrahc.paper.util.Feedback.levelUpCelebration(plugin, p);
    }
}
