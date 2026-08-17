package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.util.NumberUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sideboard huba (rola LOBBY): branding + statystyki gracza. Kazdy gracz ma
 * wlasna plansze (per-gracz statystyki), na niej tez nametagi rang, zeby prefiks
 * nad glowa dzialal (glowna plansza nie renderowalaby sie przy wlasnej).
 */
public class HubScoreboardService {

    private final UltraHcPlugin plugin;
    private BukkitTask task;
    private BukkitTask animTask;
    private int frame;
    private final Map<UUID, Integer> lineCount = new HashMap<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private static final String[] ENTRIES;
    static {
        ChatColor[] colors = ChatColor.values();
        ENTRIES = new String[colors.length];
        for (int i = 0; i < colors.length; i++) ENTRIES[i] = colors[i].toString() + ChatColor.RESET;
    }

    public HubScoreboardService(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
        animTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animateTitle, 4L, 4L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        if (animTask != null) { animTask.cancel(); animTask = null; }
    }

    public void clear(UUID uuid) {
        lineCount.remove(uuid);
    }

    private Component currentTitle() {
        List<String> frames = plugin.messages().rawList("scoreboard.title-frames");
        if (frames.isEmpty()) return plugin.messages().component("scoreboard.title", null);
        return LEGACY.deserialize(frames.get(Math.floorMod(frame, frames.size())));
    }

    private void animateTitle() {
        frame++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (board == null) continue;
            Objective o = board.getObjective("hub");
            if (o != null) o.displayName(currentTitle());
        }
    }

    private void updateAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) updateFor(p);
    }

    private void updateFor(Player player) {
        MessagesManager msg = plugin.messages();
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("hub");
        if (obj == null) {
            obj = board.registerNewObjective("hub", Criteria.DUMMY, currentTitle());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        } else {
            obj.displayName(currentTitle());
        }

        List<String> lines = buildLines(player, msg);
        int size = lines.size();
        for (int i = 0; i < size && i < ENTRIES.length; i++) {
            String entry = ENTRIES[i];
            org.bukkit.scoreboard.Team team = board.getTeam("h" + i);
            if (team == null) { team = board.registerNewTeam("h" + i); team.addEntry(entry); }
            team.prefix(SECTION.deserialize(lines.get(i)));
            Score sc = obj.getScore(entry);
            if (!sc.isScoreSet() || sc.getScore() != size - i) sc.setScore(size - i);
        }
        int prev = lineCount.getOrDefault(player.getUniqueId(), 0);
        for (int i = size; i < prev && i < ENTRIES.length; i++) board.resetScores(ENTRIES[i]);
        lineCount.put(player.getUniqueId(), size);

        nametags(board);
    }

    /** Prefiks rangi nad glowa (na wlasnej planszy gracza). */
    private void nametags(Scoreboard board) {
        if (plugin.groups() == null) return;
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            String tname = "r_" + other.getUniqueId().toString().substring(0, 10);
            org.bukkit.scoreboard.Team team = board.getTeam(tname);
            if (team == null) team = board.registerNewTeam(tname);
            team.prefix(LEGACY.deserialize(plugin.groups().groupPrefix(other)));
            if (!team.hasEntry(other.getName())) team.addEntry(other.getName());
        }
    }

    private List<String> buildLines(Player player, MessagesManager msg) {
        List<String> lines = new ArrayList<>();
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        int level = p != null ? p.getLevel() : 0;
        long xp = p != null ? p.getCredits() : 0;
        int kills = p != null ? p.getKills() : 0;
        int wins = p != null ? p.getWins() : 0;
        String rank = plugin.groups() != null ? plugin.groups().groupPrefix(player) : "";

        addSep(lines, msg);
        lines.add(legacy(msg.raw("hub.rank", Map.of("rank", rank))));
        lines.add(legacy(msg.raw("hub.level", Map.of("level", String.valueOf(level)))));
        lines.add(legacy(msg.raw("hub.xp", Map.of("xp", NumberUtil.grouped(xp)))));
        lines.add(legacy(msg.raw("hub.kills", Map.of("kills", String.valueOf(kills)))));
        lines.add(legacy(msg.raw("hub.wins", Map.of("wins", String.valueOf(wins)))));
        addSep(lines, msg);
        lines.add(legacy(msg.raw("hub.online", Map.of("online",
                String.valueOf(plugin.getServer().getOnlinePlayers().size())))));
        String footer = msg.raw("scoreboard.footer");
        if (footer != null && !footer.isBlank()) { addSep(lines, msg); lines.add(legacy(footer)); }
        return lines;
    }

    private void addSep(List<String> lines, MessagesManager msg) {
        String s = msg.raw("scoreboard.sep");
        lines.add(s == null || s.isBlank() ? "" : legacy(s));
    }

    private String legacy(String s) {
        return LEGACY.serialize(LEGACY.deserialize(s));
    }
}
