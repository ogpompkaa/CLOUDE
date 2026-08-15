package pl.ultrahc.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

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

    /** Efekt zwyciestwa wokol gracza. */
    public static void winParticles(Player p) {
        Location loc = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(par("win", Particle.FIREWORK), loc, 60, 0.5, 1.0, 0.5, 0.1);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 30, 0.5, 1.0, 0.5, 0.0);
    }

    /** Wystrzeliwuje fajerwerk (efekt zwyciestwa). */
    public static void launchFirework(Location loc) {
        if (loc.getWorld() == null) return;
        var fw = loc.getWorld().spawn(loc, org.bukkit.entity.Firework.class);
        var meta = fw.getFireworkMeta();
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE)
                .withFade(org.bukkit.Color.WHITE)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .flicker(true).trail(true).build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
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
