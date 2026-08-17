package pl.ultrahc.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Efekty dla graczy: tytuly, dzwieki i czastki. Dzwieki/czastki sa konfigurowalne
 * w config.yml (sekcja effects.*) z bezpiecznymi fallbackami w kodzie.
 */
public final class Feedback {

    private Feedback() {}

    private static FileConfiguration cfg;

    /** Podpiecie configu (wolane w onEnable). Bez niego uzywane sa fallbacki. */
    public static void configure(FileConfiguration configuration) {
        cfg = configuration;
    }

    public static void title(Player player, Component main, Component sub) {
        player.showTitle(Title.title(main, sub,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
    }

    public static void sound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    // Skroty na typowe zdarzenia (dzwiek z config.effects.sounds.*).
    public static void levelUp(Player p) { sound(p, snd("level-up", Sound.ENTITY_PLAYER_LEVELUP), 1.0f); }
    public static void buy(Player p) { sound(p, snd("buy", Sound.ENTITY_EXPERIENCE_ORB_PICKUP), 1.2f); }
    public static void kill(Player p) { sound(p, snd("kill", Sound.ENTITY_ARROW_HIT_PLAYER), 1.0f); }
    public static void win(Player p) { sound(p, snd("win", Sound.UI_TOAST_CHALLENGE_COMPLETE), 1.0f); }
    public static void error(Player p) { sound(p, snd("error", Sound.ENTITY_VILLAGER_NO), 1.0f); }

    /** Wybuch czastek w miejscu zabojstwa. */
    public static void killParticles(Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(par("kill", Particle.CRIT), loc, 30, 0.3, 0.5, 0.3, 0.1);
        loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 12, 0.3, 0.5, 0.3, 0.0);
    }

    /**
     * Bogaty efekt eliminacji w miejscu smierci: kosmetyczny piorun (bez obrazen),
     * slup czastek w gore i dzwiek. Wszystko sterowane z config.effects.elimination.*.
     */
    public static void elimination(Location loc) {
        var w = loc.getWorld();
        if (w == null) return;
        if (cfg != null && !cfg.getBoolean("effects.elimination.enabled", true)) return;

        if (cfg == null || cfg.getBoolean("effects.elimination.lightning", true)) {
            w.strikeLightningEffect(loc); // effect = wylacznie wizualny, bez obrazen
        }
        if (cfg == null || cfg.getBoolean("effects.elimination.pillar", true)) {
            Particle p = par("elimination", Particle.FLAME);
            for (double y = 0; y <= 4.0; y += 0.25) {
                w.spawnParticle(p, loc.clone().add(0, y, 0), 6, 0.15, 0.1, 0.15, 0.0);
            }
        }
        w.playSound(loc, snd("elimination", Sound.ENTITY_LIGHTNING_BOLT_THUNDER), 0.7f, 1.1f);
    }

    /** Efekt zwyciestwa wokol gracza. */
    public static void winParticles(Player p) {
        Location loc = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(par("win", Particle.FIREWORK), loc, 60, 0.5, 1.0, 0.5, 0.1);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 30, 0.5, 1.0, 0.5, 0.0);
    }

    /** Wyskakujaca liczba obrazen nad ofiara (TextDisplay, znika po chwili). */
    public static void damageIndicator(Plugin plugin, LivingEntity victim, double dmg, boolean crit) {
        if (cfg != null && !cfg.getBoolean("effects.damage-numbers", true)) return;
        var w = victim.getWorld();
        if (w == null) return;
        double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        Location loc = victim.getEyeLocation().add(ox, 0.35, oz);
        String txt = (crit ? "✹ " : "") + fmtDamage(dmg);
        TextDisplay td = w.spawn(loc, TextDisplay.class, d -> {
            d.text(Component.text(txt, crit ? NamedTextColor.GOLD : NamedTextColor.RED));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // przezroczyste tlo
            d.setShadowed(true);
            d.setSeeThrough(false);
        });
        int ttl = cfg == null ? 16 : cfg.getInt("effects.damage-numbers-ttl", 16);
        plugin.getServer().getScheduler().runTaskLater(plugin, td::remove, ttl);
    }

    /** Czastki i dzwiek przy zadaniu ciosu (mocniejsze przy krytyku). */
    public static void hitEffect(Player attacker, Entity victim, boolean crit) {
        if (cfg != null && !cfg.getBoolean("effects.hit-feedback", true)) return;
        var w = victim.getWorld();
        Location loc = victim.getLocation().add(0, 1.0, 0);
        w.spawnParticle(par("hit", Particle.DAMAGE_INDICATOR), loc, crit ? 12 : 6, 0.2, 0.3, 0.2, 0.0);
        if (crit) w.spawnParticle(Particle.ENCHANTED_HIT, loc, 16, 0.3, 0.3, 0.3, 0.1);
        attacker.playSound(attacker.getLocation(),
                crit ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
    }

    /** Efekt awansu: spirala czastek + fajerwerk wokol gracza. */
    public static void levelUpCelebration(Plugin plugin, Player p) {
        if (cfg != null && !cfg.getBoolean("effects.levelup-celebration", true)) return;
        var w = p.getWorld();
        for (int i = 0; i < 20; i++) {
            final int step = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                double angle = step * Math.PI / 5.0;
                Location l = p.getLocation().add(Math.cos(angle) * 0.8, step * 0.12, Math.sin(angle) * 0.8);
                w.spawnParticle(Particle.HAPPY_VILLAGER, l, 2, 0.05, 0.05, 0.05, 0);
                w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0.01);
            }, i);
        }
        launchFirework(p.getLocation().add(0, 1, 0));
    }

    /** Pierscien czastek + dzwiek przy wejsciu do lobby/poczekalni. */
    public static void joinRing(Player p) {
        if (cfg != null && !cfg.getBoolean("effects.join-ring", true)) return;
        var w = p.getWorld();
        Location c = p.getLocation();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            w.spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(Math.cos(a), 0.2, Math.sin(a)), 1, 0, 0, 0, 0);
        }
        p.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
    }

    private static String fmtDamage(double dmg) {
        return dmg == Math.floor(dmg)
                ? "-" + (int) dmg
                : "-" + String.format(Locale.US, "%.1f", dmg);
    }

    /** Wystrzeliwuje fajerwerk (domyslne zloto/pomarancz). */
    public static void launchFirework(Location loc) {
        launchFirework(loc, Color.YELLOW, Color.ORANGE);
    }

    /** Wystrzeliwuje fajerwerk w podanym kolorze (np. kolor druzyny zwyciezcy). */
    public static void launchFirework(Location loc, Color primary, Color fade) {
        if (loc.getWorld() == null) return;
        var fw = loc.getWorld().spawn(loc, org.bukkit.entity.Firework.class);
        var meta = fw.getFireworkMeta();
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(primary)
                .withFade(fade)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .flicker(true).trail(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    /** Unoszacy sie napis (TextDisplay) nad lokalizacja, znika po ttlTicks. */
    public static void floatingLabel(Plugin plugin, Location loc, Component text, int ttlTicks) {
        var w = loc.getWorld();
        if (w == null) return;
        TextDisplay td = w.spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(true);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, td::remove, Math.max(1, ttlTicks));
    }

    // --------------------------------------------------------- resolwery
    private static Sound snd(String key, Sound def) {
        if (cfg == null) return def;
        String name = cfg.getString("effects.sounds." + key, null);
        if (name == null) return def;
        Sound s = Registry.SOUNDS.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return s != null ? s : def;
    }

    private static Particle par(String key, Particle def) {
        if (cfg == null) return def;
        String name = cfg.getString("effects.particles." + key, null);
        if (name == null) return def;
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
