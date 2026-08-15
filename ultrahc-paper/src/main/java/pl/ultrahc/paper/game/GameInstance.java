package pl.ultrahc.paper.game;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pojedyncza instancja gry na tym arena-serwerze (DECYZJA: 1 serwer = 1 gra).
 * Trzyma stan, uczestnikow, druzyny i timeline faz. Fazy (granica, kompas,
 * arenka) sa tu WYZWALANE — realizacja mechanik dojdzie w kolejnych managerach.
 */
public class GameInstance {

    private final UltraHcPlugin plugin;
    private final World world;

    private GameState state = GameState.WAITING;
    private final List<UUID> participants = new ArrayList<>();
    private TeamManager teamManager;

    private int countdownRemaining;
    private long startMillis;
    private boolean pvpEnabled;

    // Progi faz juz "odpalone" (zeby nie powtarzac broadcastow).
    private boolean pvpFired, shrinkFired, accelerateFired, compassFired, showdownFired;

    private BukkitTask ticker;

    public GameInstance(UltraHcPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public GameState state() { return state; }
    public World world() { return world; }
    public boolean pvpEnabled() { return pvpEnabled; }
    public TeamManager teams() { return teamManager; }
    public List<UUID> participants() { return participants; }

    // ------------------------------------------------------------------ join
    public boolean addPlayer(Player player) {
        int max = cfgInt("game.max-players", 100);
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) return false;
        if (participants.size() >= max) return false;
        if (participants.contains(player.getUniqueId())) return false;

        participants.add(player.getUniqueId());
        player.teleport(world.getSpawnLocation());
        player.setGameMode(GameMode.ADVENTURE); // poczekalnia przed startem
        int min = cfgInt("game.min-players-to-countdown", 30);
        broadcast("game.waiting", Map.of("count", String.valueOf(participants.size()), "min", String.valueOf(min)));

        if (state == GameState.WAITING && participants.size() >= min) {
            startCountdown();
        }
        return true;
    }

    public void removePlayer(UUID uuid) {
        // Przed startem: po prostu usun z listy.
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            participants.remove(uuid);
            return;
        }
        // W trakcie gry: traktuj jak eliminacje (okno powrotu obsluzy DeathListener/etap 5).
        if (state == GameState.RUNNING && teamManager != null) {
            handleElimination(uuid, null);
        }
    }

    // ------------------------------------------------------------- countdown
    private void startCountdown() {
        state = GameState.COUNTDOWN;
        countdownRemaining = cfgInt("game.countdown-seconds", 180);
        broadcast("game.countdown-started", Map.of(
                "min", String.valueOf(cfgInt("game.min-players-to-countdown", 30)),
                "seconds", String.valueOf(countdownRemaining)));
        startTicker();
    }

    private void tickCountdown() {
        countdownRemaining--;
        if (countdownRemaining <= 0) {
            startGame();
            return;
        }
        // Komunikaty w kluczowych momentach.
        if (countdownRemaining == 120 || countdownRemaining == 60 || countdownRemaining == 30
                || countdownRemaining == 10 || countdownRemaining <= 5) {
            broadcast("game.countdown", Map.of("seconds", String.valueOf(countdownRemaining)));
        }
    }

    // ------------------------------------------------------------------ start
    private void startGame() {
        state = GameState.RUNNING;
        startMillis = System.currentTimeMillis();
        pvpEnabled = false;

        int teamSize = cfgInt("game.team-size", 1);
        teamManager = new TeamManager(teamSize);
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : participants) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) names.put(id, p.getName());
        }
        teamManager.buildTeams(new ArrayList<>(participants), names);

        // Rozrzuc graczy po mapie i przywroc tryb przetrwania.
        for (UUID id : participants) {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null) continue;
            p.teleport(randomSpread());
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
        }
        int noPvpMin = cfgInt("game.no-pvp-seconds", 600) / 60;
        broadcast("game.started", Map.of("minutes", String.valueOf(noPvpMin)));
        startTicker(); // dziala tez przy force-starcie z WAITING
    }

    /** Wymuszony start (admin): rusza gre niezaleznie od progu graczy. */
    public void forceStart() {
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            startGame();
        }
    }

    /** Wymuszony koniec (admin): konczy z aktualnym liderem lub bez zwyciescy. */
    public void forceEnd() {
        if (state == GameState.RUNNING) {
            endGame(resolveByTiebreak());
        } else {
            endGame(null);
        }
    }

    // ------------------------------------------------------------- game tick
    private void tickGame() {
        long elapsed = elapsedSeconds();

        int noPvp = cfgInt("game.no-pvp-seconds", 600);
        if (!pvpFired && elapsed >= noPvp) {
            pvpFired = true;
            pvpEnabled = true;
            broadcast("game.pvp-enabled", Map.of());
        }
        int shrinkStart = cfgInt("border.shrink-start-min", 10) * 60;
        if (!shrinkFired && elapsed >= shrinkStart) {
            shrinkFired = true;
            broadcast("game.border-shrinking", Map.of());
            if (plugin.border() != null) plugin.border().beginPhase1(this);
        }
        int accelerate = cfgInt("border.accelerate-min", 30) * 60;
        if (!accelerateFired && elapsed >= accelerate) {
            accelerateFired = true;
            if (plugin.border() != null) plugin.border().beginPhase2(this);
        }
        int compass = cfgInt("game.enemy-compass-unlock-min", 18) * 60;
        if (!compassFired && elapsed >= compass) {
            compassFired = true;
            broadcast("game.compass-enemy-unlocked", Map.of());
        }
        int showdown = cfgInt("arena-showdown.teleport-min", 45) * 60;
        if (!showdownFired && elapsed >= showdown) {
            showdownFired = true;
            broadcast("game.showdown", Map.of());
            if (plugin.border() != null) plugin.border().beginShowdown(this);
        }
        int hardCap = cfgInt("game.hard-time-cap-min", 90) * 60;
        if (elapsed >= hardCap) {
            endGame(resolveByTiebreak());
            return;
        }
        // Warunek zwyciestwa: zostaje <=1 zywa druzyna.
        if (teamManager != null && teamManager.aliveTeams().size() <= 1) {
            List<Team> alive = teamManager.aliveTeams();
            endGame(alive.isEmpty() ? null : alive.get(0));
        }
    }

    // --------------------------------------------------------- eliminations
    /** Wywolywane przez DeathListener (etap 5) i przez removePlayer przy wyjsciu. */
    public void handleElimination(UUID victim, UUID killerOrNull) {
        if (teamManager == null) return;
        Team victimTeam = teamManager.getTeam(victim);
        if (victimTeam == null || !victimTeam.isAlive(victim)) return;
        victimTeam.markDead(victim);

        if (killerOrNull != null) {
            Team killerTeam = teamManager.getTeam(killerOrNull);
            if (killerTeam != null && killerTeam != victimTeam) {
                killerTeam.addKill();
                // TODO(etap 5): nagrody za zabojstwo (XP+PD), glowka
            }
        }
        Player victimPlayer = plugin.getServer().getPlayer(victim);
        if (victimPlayer != null) victimPlayer.setGameMode(GameMode.SPECTATOR);

        if (victimTeam.isEliminated()) {
            broadcast("game.team-eliminated", Map.of("team", victimTeam.getName()));
        }
    }

    // ------------------------------------------------------------------- end
    public void endGame(Team winner) {
        if (state == GameState.ENDING) return;
        state = GameState.ENDING;
        if (winner != null) {
            String key = cfgInt("game.team-size", 1) == 1 ? "game.win-solo" : "game.win-team";
            String nameKey = cfgInt("game.team-size", 1) == 1 ? "player" : "team";
            broadcast(key, Map.of(nameKey, winner.getName()));
            // TODO(etap 5): nagroda za wygrana (XP+PD), wins++ dla czlonkow
        } else {
            broadcast("game.death-generic", Map.of("victim", "-"));
        }
        stopTicker();
        // Sprzatanie zleci GameManager (kasowanie swiata + nowa instancja).
        plugin.games().onInstanceEnded(this);
    }

    /** Twardy tiebreak przy wymuszonym koncu: kille -> pozostale serca. */
    private Team resolveByTiebreak() {
        if (teamManager == null) return null;
        List<Team> alive = teamManager.aliveTeams();
        if (alive.isEmpty()) return null;
        alive.sort((a, b) -> {
            if (b.getKills() != a.getKills()) return Integer.compare(b.getKills(), a.getKills());
            return Double.compare(teamHealth(b), teamHealth(a));
        });
        return alive.get(0);
    }

    private double teamHealth(Team team) {
        double sum = 0;
        for (UUID id : team.getAlive()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) sum += p.getHealth();
        }
        return sum;
    }

    // --------------------------------------------------------------- helpers
    public long elapsedSeconds() {
        if (state != GameState.RUNNING && state != GameState.ENDING) return 0;
        return (System.currentTimeMillis() - startMillis) / 1000L;
    }

    private Location randomSpread() {
        double size = world.getWorldBorder().getSize();
        double half = size / 2.0 - 10;
        var center = world.getWorldBorder().getCenter();
        int x = center.getBlockX() + ThreadLocalRandom.current().nextInt((int) -half, (int) half);
        int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt((int) -half, (int) half);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private void startTicker() {
        stopTicker();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (state == GameState.COUNTDOWN) tickCountdown();
            else if (state == GameState.RUNNING) tickGame();
        }, 20L, 20L);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public void shutdown() {
        stopTicker();
    }

    // Broadcast tylko do uczestnikow tej gry.
    private void broadcast(String key, Map<String, String> ph) {
        MessagesManager msg = plugin.messages();
        Component comp = msg.prefixed(key, ph);
        for (UUID id : participants) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.sendMessage(comp);
        }
    }

    private int cfgInt(String path, int def) {
        return plugin.configManager().raw().getInt(path, def);
    }
}
