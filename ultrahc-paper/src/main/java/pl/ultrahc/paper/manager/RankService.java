package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.List;
import java.util.Map;

/**
 * Wyswietla poziom gracza (gwiazdka) jako prefiks przy nicku w TAB i nad glowa,
 * oraz oznaczenia podium (#1/#2/#3 UHC) dla Top 3 wg poziomu. Dziala w lobby.
 */
public class RankService {

    private final UltraHcPlugin plugin;
    private BukkitTask task;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public RankService(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 20L * 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void refreshAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) refresh(p);
    }

    private void refresh(Player player) {
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile == null) return;

        String prefix = podiumTag(player) + plugin.messages().raw("rank.prefix",
                Map.of("level", String.valueOf(profile.getLevel())));

        // TAB.
        player.playerListName(LEGACY.deserialize(prefix + "&f" + player.getName()));

        // Nametag nad glowa (druzyna na glownym scoreboardzie).
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        String teamName = "uhc_" + shortId(player);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.prefix(LEGACY.deserialize(prefix));
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
    }

    /** Zwraca prefiks podium (#1/#2/#3 UHC) jesli gracz jest w Top 3 wg poziomu. */
    private String podiumTag(Player player) {
        if (plugin.leaderboards() == null) return "";
        List<Storage.LeaderboardEntry> top = plugin.leaderboards().top(Storage.LeaderboardType.LEVEL);
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            if (top.get(i).uuid().equals(player.getUniqueId())) {
                return plugin.messages().raw("rank.podium-" + (i + 1));
            }
        }
        return "";
    }

    private String shortId(Player player) {
        return player.getUniqueId().toString().substring(0, 12);
    }
}
