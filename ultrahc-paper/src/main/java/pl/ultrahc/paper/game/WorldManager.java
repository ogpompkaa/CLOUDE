package pl.ultrahc.paper.game;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;
import pl.ultrahc.paper.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Cykl zycia swiatow areny: KAZDA gra dostaje nowy, losowy swiat; po grze swiat
 * jest kasowany. Utrzymujemy pule gotowych swiatow, zeby gra nie czekala na
 * generacje (patrz DECYZJE, sekcja 1).
 *
 * <p>Uwaga wydajnosciowa: tworzenie swiata i sprawdzanie biomow blokuje watek
 * glowny (ograniczenie Bukkita). Dlatego pre-tworzymy swiat z wyprzedzeniem
 * (przy starcie serwera i po zakonczeniu gry), a nie w momencie startu gry.
 */
public class WorldManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Deque<World> ready = new ArrayDeque<>();
    private int counter;
    private boolean warnedNoChunky;

    public WorldManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Ile gotowych swiatow trzymac w puli. */
    public int poolSize() {
        return Math.max(1, config.raw().getInt("world.pool-size", 1));
    }

    /** Uzupelnia pule do rozmiaru docelowego (wywolywac na watku glownym). */
    public void ensurePool() {
        while (ready.size() < poolSize()) {
            ready.add(createArenaWorld());
        }
    }

    /** Pobiera gotowy swiat z puli (tworzy natychmiast, jesli pula pusta). */
    public World takeWorld() {
        World world = ready.poll();
        if (world == null) world = createArenaWorld();
        scheduleRefill(); // dogeneruj pule PO odstepie, jeden swiat na raz (nie stackuj lagu ze startem gry)
        return world;
    }

    /**
     * Uzupelnia pule po jednym swiecie z odstepem — generacja swiata blokuje watek
     * glowny, wiec rozkladamy ja w czasie zamiast robic natychmiast po starcie gry.
     */
    private void scheduleRefill() {
        if (ready.size() >= poolSize()) return;
        long delay = Math.max(20L, config.raw().getLong("world.pool-refill-delay-ticks", 200));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (ready.size() < poolSize()) {
                ready.add(createArenaWorld());
                scheduleRefill();
            }
        }, delay);
    }

    /** Tworzy nowy swiat areny z losowym seedem, ustawia granice startowa. */
    public World createArenaWorld() {
        var gen = config.raw().getConfigurationSection("world.generation.require-biomes");
        boolean requireBiomes = gen != null && gen.getBoolean("enabled", false);
        int maxAttempts = requireBiomes ? Math.max(1, gen.getInt("max-seed-attempts", 40)) : 1;

        World world = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String name = "uhc_arena_" + (++counter);
            long seed = ThreadLocalRandom.current().nextLong();
            plugin.getLogger().info("[UltraHC] Generuje swiat areny " + name + " (seed=" + seed + ", proba " + attempt + ").");
            world = new WorldCreator(name).seed(seed).createWorld();
            if (world == null) continue;
            setupBorder(world);
            if (!requireBiomes || hasRequiredBiomes(world, gen)) {
                startPregen(world);
                return world;
            }
            plugin.getLogger().warning("[UltraHC] Swiat " + name + " nie ma wymaganych biomow — generuje kolejny.");
            deleteWorld(world);
            world = null;
        }
        plugin.getLogger().warning("[UltraHC] Nie znaleziono swiata z wymaganymi biomami w limicie prob — uzywam ostatniego (fallback dla Yeti: pasywka na dowolnym sniegu/lodzie).");
        if (world == null) {
            world = new WorldCreator("uhc_arena_" + (++counter)).seed(ThreadLocalRandom.current().nextLong()).createWorld();
            if (world != null) setupBorder(world);
        }
        return world;
    }

    /** Ustawia granice startowa (srodek = spawn, srednica = border.start). */
    public void setupBorder(World world) {
        applyGameRules(world);
        double start = config.raw().getDouble("border.start", 1000);
        var border = world.getWorldBorder();
        border.setCenter(world.getSpawnLocation());
        border.setSize(start);
        border.setWarningDistance(config.raw().getInt("border.warning-blocks", 30));
        border.setWarningTime(config.raw().getInt("border.warning-seconds", 5));
    }

    /**
     * Reguly gry UHC per-swiat (RDZEN trybu). Domyslnie: brak naturalnej regeneracji
     * (leczenie tylko przez zlote jablka/mikstury/klasy), trudnosc HARD, brak
     * phantomow i cyklu pogody. Wszystko sterowane z config.yml (world.rules.*).
     */
    public void applyGameRules(World world) {
        var c = config.raw();
        String diff = c.getString("world.rules.difficulty", "HARD");
        try {
            world.setDifficulty(Difficulty.valueOf(diff.toUpperCase()));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[UltraHC] Nieznana trudnosc '" + diff + "' — ustawiam HARD.");
            world.setDifficulty(Difficulty.HARD);
        }
        world.setGameRule(GameRule.NATURAL_REGENERATION, c.getBoolean("world.rules.natural-regeneration", false));
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, c.getBoolean("world.rules.daylight-cycle", true));
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, c.getBoolean("world.rules.weather-cycle", false));
        world.setGameRule(GameRule.DO_INSOMNIA, c.getBoolean("world.rules.insomnia", false));
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, c.getBoolean("world.rules.announce-advancements", false));
        world.setGameRule(GameRule.KEEP_INVENTORY, c.getBoolean("world.rules.keep-inventory", false));
        world.setGameRule(GameRule.MOB_GRIEFING, c.getBoolean("world.rules.mob-griefing", true));
        world.setGameRule(GameRule.DO_FIRE_TICK, c.getBoolean("world.rules.fire-tick", true));
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, c.getBoolean("world.rules.show-death-messages", true));
        // Mniejszy promien trzymanych chunkow spawnu = mniejsze obciazenie bezczynnego swiata z puli.
        world.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, c.getInt("world.rules.spawn-chunk-radius", 2));
        // Pogoda wyczyszczona na start (spojnie z wylaczonym cyklem, jesli wylaczony).
        if (!c.getBoolean("world.rules.weather-cycle", false)) world.setStorm(false);
    }

    /**
     * Uruchamia asynchroniczna pre-generacje reszty mapy przez Chunky (jesli obecny).
     * Odciaza watek glowny w trakcie gry — chunki sa gotowe zanim gracze do nich dojda.
     */
    private void startPregen(World world) {
        if (!config.raw().getBoolean("world.pregen.enabled", true)) return;
        if (plugin.getServer().getPluginManager().getPlugin("Chunky") == null) {
            if (!warnedNoChunky) {
                warnedNoChunky = true;
                plugin.getLogger().info("[UltraHC] Pre-generacja wlaczona, ale brak pluginu Chunky — "
                        + "generacja swiatow bedzie obciazac watek glowny. Zainstaluj Chunky dla plynniejszych startow gier.");
            }
            return;
        }
        int radius = (int) (config.raw().getDouble("border.start", 1000) / 2
                + config.raw().getInt("world.pregen.pad", 32));
        var console = plugin.getServer().getConsoleSender();
        var loc = world.getSpawnLocation();
        plugin.getServer().dispatchCommand(console, "chunky world " + world.getName());
        plugin.getServer().dispatchCommand(console, "chunky center " + loc.getBlockX() + " " + loc.getBlockZ());
        plugin.getServer().dispatchCommand(console, "chunky radius " + radius);
        plugin.getServer().dispatchCommand(console, "chunky start");
        plugin.getLogger().info("[UltraHC] Chunky: pre-generacja " + world.getName() + " (promien " + radius + ").");
    }

    /** Kasuje swiat: rozladunek + usuniecie folderu z dysku. */
    public void deleteWorld(World world) {
        if (world == null) return;
        Path folder = world.getWorldFolder().toPath();
        boolean unloaded = plugin.getServer().unloadWorld(world, false);
        if (!unloaded) {
            plugin.getLogger().warning("[UltraHC] Nie udalo sie rozladowac swiata " + world.getName() + ".");
            return;
        }
        deleteRecursively(folder);
    }

    /** Sprawdza (best-effort), czy w poblizu spawnu wystepuje ktorykolwiek wymagany biom. */
    private boolean hasRequiredBiomes(World world, org.bukkit.configuration.ConfigurationSection gen) {
        List<String> required = gen.getStringList("biomes").stream().map(s -> s.toLowerCase()).toList();
        if (required.isEmpty()) return true;
        int radius = gen.getInt("search-radius", 500);
        int step = 200; // rzadkie probkowanie, zeby ograniczyc ladowanie chunkow
        int cx = world.getSpawnLocation().getBlockX();
        int cz = world.getSpawnLocation().getBlockZ();
        Set<String> found = new HashSet<>();
        for (int x = cx - radius; x <= cx + radius; x += step) {
            for (int z = cz - radius; z <= cz + radius; z += step) {
                Biome biome = world.getBiome(x, 64, z);
                found.add(biome.getKey().getKey());
            }
        }
        return required.stream().anyMatch(found::contains);
    }

    private void deleteRecursively(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    plugin.getLogger().warning("[UltraHC] Nie usunieto pliku " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("[UltraHC] Blad kasowania swiata: " + e.getMessage());
        }
    }

    /** Sprzatanie przy wylaczaniu — kasuje swiaty z puli. */
    public void shutdown() {
        for (World w : ready) deleteWorld(w);
        ready.clear();
    }
}
