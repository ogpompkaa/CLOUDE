package pl.ultrahc.paper.manager;

import org.bukkit.command.CommandSender;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sezony (bez auto-daty — sterowane przez admina). Start: komunikat. Koniec:
 * wyplata nagrod Top 3 wg poziomu, reset poziomow i receptur (klasy zostaja).
 */
public class SeasonManager {

    private final UltraHcPlugin plugin;
    private final ShopCurrencyManager currency;

    public SeasonManager(UltraHcPlugin plugin, ShopCurrencyManager currency) {
        this.plugin = plugin;
        this.currency = currency;
    }

    public void startSeason(CommandSender by) {
        plugin.getServer().broadcast(plugin.messages().prefixed("season.started", null));
        plugin.getLogger().info("[UltraHC] Sezon rozpoczety przez " + by.getName() + ".");
    }

    /** Koniec sezonu: wyplata Top 3 (async DB) + reset poziomow/receptur. */
    public void endSeason(CommandSender by) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Storage.LeaderboardEntry> top = plugin.profiles().storage().topBy(Storage.LeaderboardType.LEVEL, 3);
                var rewards = plugin.configManager().raw().getConfigurationSection("season.top-rewards");
                int rank = 1;
                for (Storage.LeaderboardEntry entry : top) {
                    long amount = rewards == null ? 0 : rewards.getLong(String.valueOf(rank), 0);
                    if (amount > 0) awardCurrency(entry.uuid(), entry.name(), amount, rank);
                    rank++;
                }
                plugin.profiles().storage().resetSeason();
            } catch (Exception e) {
                plugin.getLogger().severe("[UltraHC] Blad konczenia sezonu: " + e.getMessage());
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().broadcast(plugin.messages().prefixed("season.ended", null));
                if (plugin.leaderboards() != null) plugin.leaderboards().refresh();
                plugin.getLogger().info("[UltraHC] Sezon zakonczony przez " + by.getName() + ".");
            });
        });
    }

    /** Dodaje XP graczowi (online: cache; offline: bezposrednio w DB). */
    private void awardCurrency(UUID uuid, String name, long amount, int rank) {
        PlayerProfile online = plugin.profiles().get(uuid);
        try {
            if (online != null) {
                currency.add(online, amount);
                plugin.profiles().saveNow(online);
            } else {
                PlayerProfile p = plugin.profiles().storage().loadProfile(uuid, name);
                currency.add(p, amount);
                plugin.profiles().storage().saveProfile(p);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[UltraHC] Blad wyplaty sezonowej dla " + name + ": " + e.getMessage());
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getServer().broadcast(plugin.messages().prefixed("season.reward", Map.of(
                        "rank", String.valueOf(rank),
                        "xp", pl.ultrahc.paper.util.NumberUtil.grouped(amount),
                        "player", name))));
    }
}
