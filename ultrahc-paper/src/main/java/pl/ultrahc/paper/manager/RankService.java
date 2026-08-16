package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

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

        String prefix = plugin.groups().fullPrefix(player);           // ranga serwerowa + poziom/podium
        String nameColor = plugin.groups().nameColor(player);

        // TAB — nick z ranga + naglowek/stopka.
        player.playerListName(LEGACY.deserialize(prefix + nameColor + player.getName()));
        player.sendPlayerListHeaderAndFooter(
                LEGACY.deserialize(plugin.messages().raw("tablist.header")),
                LEGACY.deserialize(plugin.messages().raw("tablist.footer",
                        Map.of("online", String.valueOf(plugin.getServer().getOnlinePlayers().size())))));

        // Nametag nad glowa (druzyna na glownym scoreboardzie).
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        String teamName = "uhc_" + shortId(player);
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.prefix(LEGACY.deserialize(prefix));
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
    }

    /** Sprzatanie przy wyjsciu gracza — usuwa jego team z glownego scoreboardu. */
    public void cleanup(Player player) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("uhc_" + shortId(player));
        if (team != null) team.unregister();
    }

    private String shortId(Player player) {
        return player.getUniqueId().toString().substring(0, 12);
    }
}
