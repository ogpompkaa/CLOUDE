package pl.ultrahc.paper.game;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    // Reconnect (okno powrotu) i combat-tag (zabojca przy combat-logu).
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> pendingReconnect = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    private final Map<UUID, Integer> killStreak = new HashMap<>();

    // Progi faz juz "odpalone" (zeby nie powtarzac broadcastow).
    private boolean pvpFired, shrinkFired, accelerateFired, compassFired, showdownFired;

    private BukkitTask ticker;

    public GameInstance(UltraHcPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public GameState state() { return state; }
    public int countdownRemaining() { return countdownRemaining; }
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
        // Poczekalnia przed startem: trwaly swiat lobby areny (nie swiat meczu).
        if (plugin.arenaLobby() != null) {
            plugin.arenaLobby().send(player);
        } else {
            player.teleport(world.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
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
        // Jawne /uhc leave w trakcie gry = natychmiastowa eliminacja.
        if (state == GameState.RUNNING && teamManager != null) {
            handleElimination(uuid, null);
        }
    }

    // ------------------------------------------------------ reconnect / tag
    /** Zapamietuje ostatniego napastnika (combat-tag) — do kary za combat-log. */
    public void recordHit(UUID victim, UUID attacker) {
        lastAttacker.put(victim, attacker);
        lastHitTime.put(victim, System.currentTimeMillis());
    }

    private boolean combatTagged(UUID uuid) {
        int tag = cfgInt("combat.tag-seconds", 10);
        return System.currentTimeMillis() - lastHitTime.getOrDefault(uuid, 0L) <= tag * 1000L;
    }

    /** Rozlaczenie w trakcie gry: uruchamia okno powrotu; po nim eliminacja. */
    public void onDisconnect(UUID uuid) {
        if (state != GameState.RUNNING || teamManager == null) return;
        Team team = teamManager.getTeam(uuid);
        if (team == null || !team.isAlive(uuid)) return;
        if (pendingReconnect.containsKey(uuid)) return;

        int grace = cfgInt("game.reconnect-grace-seconds", 180);
        var task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingReconnect.remove(uuid);
            // Combat-log: jesli byl otagowany, kill leci do napastnika.
            UUID killer = combatTagged(uuid) ? lastAttacker.get(uuid) : null;
            handleElimination(uuid, killer);
        }, grace * 20L);
        pendingReconnect.put(uuid, task);
    }

    /** Powrot gracza w oknie: anuluje zaplanowana eliminacje. */
    public void onReconnect(UUID uuid) {
        var task = pendingReconnect.remove(uuid);
        if (task != null) task.cancel();
    }

    // ------------------------------------------------------------- countdown
    private void startCountdown() {
        state = GameState.COUNTDOWN;
        countdownRemaining = cfgInt("game.countdown-seconds", 180);
        broadcast("game.countdown-started", Map.of(
                "min", String.valueOf(cfgInt("game.min-players-to-countdown", 30)),
                "seconds", String.valueOf(countdownRemaining)));
        soundAll(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); // dzwiek startu odliczania
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
            if (countdownRemaining <= 5) {
                // Ostatnie sekundy: rosnacy pitch + wielki tytul odliczania.
                soundAll(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f + (5 - countdownRemaining) * 0.15f);
                var main = plugin.messages().component("title.countdown-main",
                        Map.of("seconds", String.valueOf(countdownRemaining)));
                var sub = plugin.messages().component("title.countdown-sub", null);
                for (UUID id : participants) {
                    Player p = plugin.getServer().getPlayer(id);
                    if (p != null) pl.ultrahc.paper.util.Feedback.title(p, main, sub);
                }
            } else {
                soundAll(org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f);
            }
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
        // Sklad party czytany z DB (dziala tez cross-server: party z lobby, gra na arenie).
        Map<UUID, String> partyMap;
        try {
            partyMap = plugin.profiles().storage().loadPartyIds(participants);
        } catch (Exception e) {
            plugin.getLogger().warning("[UltraHC] Blad odczytu party: " + e.getMessage());
            partyMap = Map.of();
        }
        teamManager.buildTeams(new ArrayList<>(participants), names, partyMap::get);

        // Rozrzuc graczy po mapie i przywroc tryb przetrwania.
        for (UUID id : participants) {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null) continue;
            p.teleport(randomSpread());
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();   // czysty start UHC (usuwa m.in. przedmiot huba z poczekalni)
            p.setHealth(20.0);
            p.setFoodLevel(20);
            // Kit startowy wybranej klasy.
            if (plugin.classes() != null) {
                var prof = plugin.profiles().get(id);
                plugin.classes().applyKit(p, prof != null ? prof.getSelectedClass() : "civil");
            }
        }
        int noPvpMin = cfgInt("game.no-pvp-seconds", 600) / 60;
        broadcast("game.started", Map.of("minutes", String.valueOf(noPvpMin)));
        titleAll("title.start-main", "title.start-sub");       // wielki tytul STARTu
        soundAll(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f); // jasny dzwiek startu gry
        if (plugin.rewards() != null) plugin.rewards().startAccrual(this);
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
            titleAll("title.pvp-main", "title.pvp-sub");
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
            titleAll("title.showdown-main", "title.showdown-sub");
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
        killStreak.remove(victim); // koniec serii ofiary

        if (killerOrNull != null) {
            Team killerTeam = teamManager.getTeam(killerOrNull);
            if (killerTeam != null && killerTeam != victimTeam) {
                killerTeam.addKill();
                if (plugin.rewards() != null) plugin.rewards().grantKill(killerOrNull);
                Player killerPlayer = plugin.getServer().getPlayer(killerOrNull);
                if (killerPlayer != null) pl.ultrahc.paper.util.Feedback.kill(killerPlayer);
                checkKillStreak(killerOrNull);
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
        if (plugin.rewards() != null) plugin.rewards().stopAccrual();
        if (winner != null) {
            String key = cfgInt("game.team-size", 1) == 1 ? "game.win-solo" : "game.win-team";
            String nameKey = cfgInt("game.team-size", 1) == 1 ? "player" : "team";
            broadcast(key, Map.of(nameKey, winner.getName()));
            if (plugin.rewards() != null) plugin.rewards().grantWin(winner.getMembers());
            for (UUID id : winner.getMembers()) {
                Player wp = plugin.getServer().getPlayer(id);
                if (wp != null) {
                    pl.ultrahc.paper.util.Feedback.title(wp,
                            plugin.messages().component("title.win-main", null),
                            plugin.messages().component("title.win-sub", null));
                    pl.ultrahc.paper.util.Feedback.win(wp);
                    pl.ultrahc.paper.util.Feedback.winParticles(wp);
                }
            }
            scheduleWinFireworks(winner); // pokaz fajerwerkow nad zwyciezcami (kilka salw)
        } else {
            broadcast("game.death-generic", Map.of("victim", "-"));
        }
        if (plugin.rewards() != null) plugin.rewards().saveParticipants(participants);
        // Quest: rozegrana gra dla wszystkich uczestnikow.
        if (plugin.quests() != null) {
            for (UUID id : participants) {
                plugin.quests().increment(id, pl.ultrahc.paper.manager.QuestsManager.Objective.GAMES_PLAYED, 1);
            }
        }
        stopTicker();
        // Sprzatanie zleci GameManager (kasowanie swiata + nowa instancja).
        plugin.games().onInstanceEnded(this);
    }

    /** Pokaz fajerwerkow nad zwyciezcami: kilka salw w odstepach (konfigurowalny). */
    private void scheduleWinFireworks(Team winner) {
        int shots = Math.max(1, cfgInt("effects.win.firework-shots", 6));
        long interval = Math.max(1, cfgInt("effects.win.firework-interval-ticks", 12));
        for (int i = 0; i < shots; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : winner.getMembers()) {
                    Player wp = plugin.getServer().getPlayer(id);
                    if (wp != null) pl.ultrahc.paper.util.Feedback.launchFirework(wp.getLocation().add(0, 1, 0));
                }
            }, i * interval);
        }
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
        var center = world.getWorldBorder().getCenter();
        int half = (int) (size / 2.0 - 10);
        if (half < 1) { // granica za mala na rozrzut — spawnuj na srodku
            return surfaceAt(center.getBlockX(), center.getBlockZ());
        }
        // Losuj punkty i wybierz pierwszy BEZPIECZNY (lad, nie woda/lawa/jaskinia/drzewo).
        int attempts = Math.max(1, plugin.configManager().raw().getInt("game.spawn-attempts", 30));
        Location fallback = null;
        for (int i = 0; i < attempts; i++) {
            int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-half, half);
            int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-half, half);
            Location loc = surfaceAt(x, z);
            if (fallback == null) fallback = loc;
            if (isSafeSpawn(loc)) return loc;
        }
        return fallback != null ? fallback : surfaceAt(center.getBlockX(), center.getBlockZ());
    }

    /** Lokalizacja na powierzchni (stopy tuz nad najwyzszym blokiem), wysrodkowana. */
    private Location surfaceAt(int x, int z) {
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /** Czy punkt nadaje sie na spawn: staly lad pod stopami, brak cieczy/ognia/drzewa. */
    private boolean isSafeSpawn(Location loc) {
        Block ground = loc.clone().subtract(0, 1, 0).getBlock();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Material g = ground.getType();
        if (!g.isSolid()) return false;                       // np. woda/lawa maja isSolid()=false
        String gn = g.name();
        if (gn.endsWith("_LEAVES") || gn.endsWith("_LOG") || gn.equals("CACTUS")
                || gn.equals("MAGMA_BLOCK") || gn.equals("CAMPFIRE")) return false; // nie na drzewie/pulapce
        if (feet.isLiquid() || head.isLiquid()) return false;  // stopy/glowa w wodzie/lawie
        if (feet.getType() == Material.FIRE) return false;
        return feet.isPassable() && head.isPassable();         // miejsce na gracza
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
        for (var task : pendingReconnect.values()) task.cancel();
        pendingReconnect.clear();
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

    /** Zlicza serie zabojstw i przy progu robi broadcast + title dla zabojcy. */
    private void checkKillStreak(UUID killer) {
        int streak = killStreak.merge(killer, 1, Integer::sum);
        var milestones = plugin.configManager().raw().getIntegerList("combat.killstreak-milestones");
        if (!milestones.contains(streak)) return;
        Player p = plugin.getServer().getPlayer(killer);
        if (p == null) return;
        broadcast("killstreak.broadcast", Map.of("player", p.getName(), "streak", String.valueOf(streak)));
        pl.ultrahc.paper.util.Feedback.title(p,
                plugin.messages().component("killstreak.title-main", Map.of("streak", String.valueOf(streak))),
                plugin.messages().component("killstreak.title-sub", Map.of()));
    }

    // Dzwiek dla wszystkich uczestnikow.
    private void soundAll(org.bukkit.Sound sound, float pitch) {
        for (UUID id : participants) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, 1.0f, pitch);
        }
    }

    private int cfgInt(String path, int def) {
        return plugin.configManager().raw().getInt(path, def);
    }

    // Title na srodku ekranu dla wszystkich uczestnikow.
    private void titleAll(String mainKey, String subKey) {
        var main = plugin.messages().component(mainKey, null);
        var sub = plugin.messages().component(subKey, null);
        for (UUID id : participants) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) pl.ultrahc.paper.util.Feedback.title(p, main, sub);
        }
    }
}
