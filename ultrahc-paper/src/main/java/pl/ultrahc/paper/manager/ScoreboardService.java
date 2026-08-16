package pl.ultrahc.paper.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.GameState;
import pl.ultrahc.paper.game.Team;
import pl.ultrahc.paper.util.NumberUtil;
import pl.ultrahc.paper.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tablica boczna "ULTRAHC" odwzorowujaca uklad ze zrzutu. Ten SAM uklad w solo,
 * tylko sekcja wspoltowarzyszy znika (druzyna 1-osobowa) — degradacja, nie drugi
 * osobny widok. Wszystkie etykiety z messages.yml (scoreboard.*).
 */
public class ScoreboardService {

    private final UltraHcPlugin plugin;
    private BukkitTask task;
    private BukkitTask animTask;
    private int frame;
    private final java.util.Map<java.util.UUID, Integer> lineCount = new java.util.HashMap<>();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    /** Stale, niewidzialne kotwice-wpisy (po jednej na linie) — nie zmieniaja sie, wiec brak migotania. */
    private static final String[] ENTRIES;
    static {
        ChatColor[] colors = ChatColor.values();
        ENTRIES = new String[colors.length];
        for (int i = 0; i < colors.length; i++) ENTRIES[i] = colors[i].toString() + ChatColor.RESET;
    }

    public ScoreboardService(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
        // Szybsza animacja samego tytulu (co 4 ticki).
        animTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animateTitle, 4L, 4L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        if (animTask != null) { animTask.cancel(); animTask = null; }
    }

    /** Sprzatanie przy wyjsciu gracza. */
    public void clear(java.util.UUID uuid) {
        lineCount.remove(uuid);
    }

    /** Aktualny (animowany) tytul scoreboardu. */
    private net.kyori.adventure.text.Component currentTitle() {
        List<String> frames = plugin.messages().rawList("scoreboard.title-frames");
        if (frames.isEmpty()) return plugin.messages().component("scoreboard.title", null);
        return LEGACY.deserialize(frames.get(Math.floorMod(frame, frames.size())));
    }

    private void animateTitle() {
        frame++;
        if (plugin.games() == null) return;
        GameInstance game = plugin.games().current();
        if (game == null) return;
        var title = currentTitle();
        for (Player player : participantsOnline(game)) {
            var board = player.getScoreboard();
            if (board == null) continue;
            Objective obj = board.getObjective("uhc");
            if (obj != null) obj.displayName(title);
        }
    }

    private void updateAll() {
        if (plugin.games() == null) return;
        GameInstance game = plugin.games().current();
        if (game == null) return;
        for (Player player : participantsOnline(game)) {
            updateFor(player, game);
        }
    }

    /** Uczestnicy gry, ktorzy sa online (dziala i w poczekalni, i na mapie meczu). */
    private List<Player> participantsOnline(GameInstance game) {
        List<Player> out = new ArrayList<>();
        for (UUID id : game.participants()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private void updateFor(Player player, GameInstance game) {
        MessagesManager msg = plugin.messages();
        Scoreboard board = player.getScoreboard();
        // Wlasna plansza gracza (unikamy glownej wspoldzielonej).
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("uhc");
        if (obj == null) {
            obj = board.registerNewObjective("uhc", Criteria.DUMMY, currentTitle());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(currentTitle());
        }

        // Wzorzec bez migotania: stale niewidzialne wpisy (kotwice) + zmienny prefiks teamu.
        List<String> lines = buildLines(player, game, msg);
        int size = lines.size();
        for (int i = 0; i < size && i < ENTRIES.length; i++) {
            String entry = ENTRIES[i];
            org.bukkit.scoreboard.Team team = board.getTeam("l" + i);
            if (team == null) {
                team = board.registerNewTeam("l" + i);
                team.addEntry(entry);
            }
            team.prefix(SECTION.deserialize(lines.get(i)));
            Score sc = obj.getScore(entry);
            if (!sc.isScoreSet() || sc.getScore() != size - i) sc.setScore(size - i);
        }
        // Ukryj nadmiarowe linie (tylko gdy liczba linii zmalala).
        int prev = lineCount.getOrDefault(player.getUniqueId(), 0);
        for (int i = size; i < prev && i < ENTRIES.length; i++) {
            board.resetScores(ENTRIES[i]);
        }
        lineCount.put(player.getUniqueId(), size);

        healthBelowName(board, game);
        colorNametags(player, game, board);
        actionBar(player, game, msg);
    }

    /** Liczba serc pod nickiem kazdego gracza (klasyka UHC). Sterowane configiem. */
    private void healthBelowName(Scoreboard board, GameInstance game) {
        boolean show = plugin.configManager().raw().getBoolean("game.show-health-below-name", true);
        Objective health = board.getObjective("health");
        if (show && game.state() == GameState.RUNNING) {
            if (health == null) {
                health = board.registerNewObjective("health", Criteria.HEALTH, LEGACY.deserialize("&c❤"));
                health.setDisplaySlot(DisplaySlot.BELOW_NAME); // auto-aktualizacja z HP gracza
            }
        } else if (health != null) {
            health.unregister();
        }
    }

    /** HUD na action barze podczas gry: zywi, granica, kille druzyny. */
    private void actionBar(Player player, GameInstance game, MessagesManager msg) {
        if (game.state() != GameState.RUNNING || game.teams() == null) return;
        var myTeam = game.teams().getTeam(player.getUniqueId());
        int kills = myTeam != null ? myTeam.getKills() : 0;
        player.sendActionBar(msg.component("actionbar.hud", Map.of(
                "phase", phaseLabel(game, msg),
                "alive", String.valueOf(game.teams().alivePlayers()),
                "size", NumberUtil.oneDecimalComma(game.world().getWorldBorder().getSize()),
                "kills", String.valueOf(kills))));

        // Ostrzezenie dzwiekowe przy niskim HP (tylko zywi w survivalu).
        int lowHearts = plugin.configManager().raw().getInt("combat.low-health-hearts", 4);
        if (lowHearts > 0 && player.getGameMode() == org.bukkit.GameMode.SURVIVAL
                && player.getHealth() > 0 && player.getHealth() <= lowHearts * 2.0) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, 0.6f);
        }
    }

    /** Etykieta biezacej fazy gry na action barze (odliczanie PvP / kurczenia / arenka). */
    private String phaseLabel(GameInstance game, MessagesManager msg) {
        var cfg = plugin.configManager().raw();
        long elapsed = game.elapsedSeconds();
        int noPvp = cfg.getInt("game.no-pvp-seconds", 600);
        int shrinkStart = cfg.getInt("border.shrink-start-min", 10) * 60;
        int showdown = cfg.getInt("arena-showdown.teleport-min", 45) * 60;
        if (elapsed < noPvp) {
            return msg.raw("actionbar.phase-pvp", Map.of("time", TimeUtil.ms(noPvp - elapsed)));
        }
        if (elapsed < shrinkStart) {
            return msg.raw("actionbar.phase-shrink-soon", Map.of("time", TimeUtil.ms(shrinkStart - elapsed)));
        }
        if (elapsed < showdown) {
            return msg.raw("actionbar.phase-shrinking", Map.of());
        }
        return msg.raw("actionbar.phase-showdown", Map.of());
    }

    /** Koloruje nicki nad glowa z perspektywy widza: sojusznik zielony, wrog czerwony. */
    private void colorNametags(Player viewer, GameInstance game, Scoreboard board) {
        if (game.teams() == null) return;
        org.bukkit.scoreboard.Team mates = teamColored(board, "mates", net.kyori.adventure.text.format.NamedTextColor.GREEN);
        org.bukkit.scoreboard.Team foes = teamColored(board, "foes", net.kyori.adventure.text.format.NamedTextColor.RED);
        // Semantyka druzyny: widac niewidzialnych sojusznikow, twardy brak FF (2. warstwa).
        mates.setCanSeeFriendlyInvisibles(true);
        mates.setAllowFriendlyFire(false);
        foes.setCanSeeFriendlyInvisibles(false);
        var myTeam = game.teams().getTeam(viewer.getUniqueId());
        for (Player other : game.world().getPlayers()) {
            if (other.equals(viewer)) continue;
            var ot = game.teams().getTeam(other.getUniqueId());
            boolean ally = myTeam != null && ot == myTeam;
            org.bukkit.scoreboard.Team target = ally ? mates : foes;
            org.bukkit.scoreboard.Team opposite = ally ? foes : mates;
            opposite.removeEntry(other.getName());
            if (!target.hasEntry(other.getName())) target.addEntry(other.getName());
        }
    }

    private org.bukkit.scoreboard.Team teamColored(Scoreboard board, String name, net.kyori.adventure.text.format.NamedTextColor color) {
        org.bukkit.scoreboard.Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.color(color);
        }
        return team;
    }

    private List<String> buildLines(Player player, GameInstance game, MessagesManager msg) {
        List<String> lines = new ArrayList<>();
        long currency = 0;
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile != null) currency = profile.getCredits();

        if (game.state() == GameState.WAITING || game.state() == GameState.COUNTDOWN || game.teams() == null) {
            // Osobny widok POCZEKALNI: info o grze + statystyki gracza (nie uklad meczu).
            var cfg = plugin.configManager().raw();
            int min = cfg.getInt("game.min-players-to-countdown", 30);
            String mode = pl.ultrahc.paper.manager.InstanceManager.modeName(cfg.getInt("game.team-size", 1));
            int level = profile != null ? profile.getLevel() : 0;
            int kills = profile != null ? profile.getKills() : 0;
            int wins = profile != null ? profile.getWins() : 0;

            lines.add(legacy(msg.raw("scoreboard.lobby-state")));
            lines.add(legacy(msg.raw("scoreboard.lobby-mode", Map.of("mode", mode))));
            lines.add(legacy(msg.raw("scoreboard.lobby-players", Map.of(
                    "count", String.valueOf(game.participants().size()), "min", String.valueOf(min)))));
            if (game.state() == GameState.COUNTDOWN) {
                lines.add(legacy(msg.raw("scoreboard.lobby-countdown",
                        Map.of("time", TimeUtil.ms(game.countdownRemaining())))));
            }
            lines.add("");
            lines.add(legacy(msg.raw("scoreboard.lobby-stats")));
            lines.add(legacy(msg.raw("scoreboard.lobby-level", Map.of("level", String.valueOf(level)))));
            lines.add(legacy(msg.raw("scoreboard.currency-label", Map.of("amount", NumberUtil.grouped(currency)))));
            lines.add(legacy(msg.raw("scoreboard.lobby-kills", Map.of("kills", String.valueOf(kills)))));
            lines.add(legacy(msg.raw("scoreboard.lobby-wins", Map.of("wins", String.valueOf(wins)))));
            String lobbyFooter = msg.raw("scoreboard.footer");
            if (lobbyFooter != null && !lobbyFooter.isBlank()) { lines.add(""); lines.add(legacy(lobbyFooter)); }
            return lines;
        }

        var teams = game.teams();
        Team myTeam = teams.getTeam(player.getUniqueId());

        // Top 3 druzyny wg killi.
        int pos = 1;
        for (Team t : teams.topByKills(3)) {
            lines.add(legacy(msg.raw("scoreboard.top-line", Map.of(
                    "pos", String.valueOf(pos++),
                    "team", t.getName(),
                    "kills", String.valueOf(t.getKills())))));
        }
        lines.add("");

        // Marker ">" druzyny gracza.
        if (myTeam != null) {
            lines.add(legacy(msg.raw("scoreboard.self-marker", Map.of(
                    "team", myTeam.getName(),
                    "kills", String.valueOf(myTeam.getKills())))));
            // Wspoltowarzysze (znika w solo — druzyna 1-osobowa).
            for (UUID mate : myTeam.getMembers()) {
                if (mate.equals(player.getUniqueId())) continue;
                String mateName = nameOf(mate);
                lines.add(legacy(msg.raw("scoreboard.teammate", Map.of("name", mateName))));
            }
        }
        lines.add("");

        // Zlota liczba = aktualny rozmiar granicy.
        double borderSize = game.world().getWorldBorder().getSize();
        lines.add(legacy(msg.raw("scoreboard.border-line", Map.of("size", NumberUtil.oneDecimalComma(borderSize)))));
        lines.add(legacy(msg.raw("scoreboard.currency-label", Map.of("amount", NumberUtil.grouped(currency)))));
        lines.add(legacy(msg.raw("scoreboard.alive-label", Map.of("count", String.valueOf(teams.alivePlayers())))));
        lines.add("");

        // Timer HH:MM:SS.
        lines.add(legacy(msg.raw("scoreboard.timer", Map.of("time", TimeUtil.hms(game.elapsedSeconds())))));
        // Opcjonalna stopka (branding).
        String footer = msg.raw("scoreboard.footer");
        if (footer != null && !footer.isBlank()) {
            lines.add("");
            lines.add(legacy(footer));
        }
        return lines;
    }

    private String nameOf(UUID uuid) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) return p.getName();
        PlayerProfile profile = plugin.profiles().get(uuid);
        return profile != null ? profile.getName() : uuid.toString().substring(0, 8);
    }

    private String legacy(String withAmpersand) {
        // Konwersja &-kodow na sekcje (wpisy scoreboardu to legacy stringi).
        return LEGACY.serialize(LEGACY.deserialize(withAmpersand));
    }

}
