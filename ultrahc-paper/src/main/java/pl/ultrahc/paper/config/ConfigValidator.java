package pl.ultrahc.paper.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Walidacja kluczowych wartosci config.yml — ostrzega (nie przerywa startu) gdy
 * ustawienia sa niespojne, zeby wychwycic bledy strojenia zanim zepsuja rozgrywke.
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /** Zwraca liste ostrzezen; pusta = konfiguracja spojna. */
    public static List<String> validate(FileConfiguration c) {
        List<String> w = new ArrayList<>();

        int teamSize = c.getInt("game.team-size", 1);
        if (teamSize < 1 || teamSize > 4) w.add("game.team-size=" + teamSize + " (oczekiwane 1-4).");

        int min = c.getInt("game.min-players-to-countdown", 30);
        int max = c.getInt("game.max-players", 100);
        if (min > max) w.add("game.min-players-to-countdown (" + min + ") > game.max-players (" + max + ").");

        if (c.getInt("game.countdown-seconds", 180) < 0) w.add("game.countdown-seconds < 0.");
        if (c.getInt("game.no-pvp-seconds", 600) < 0) w.add("game.no-pvp-seconds < 0.");

        if (c.getDouble("border.start", 1000) <= 0) w.add("border.start musi byc > 0.");
        if (c.getDouble("border.blocks-per-min", 24) <= 0) w.add("border.blocks-per-min musi byc > 0.");
        if (c.getDouble("border.blocks-per-min-fast", 30) <= 0) w.add("border.blocks-per-min-fast musi byc > 0.");

        int shrink = c.getInt("border.shrink-start-min", 10);
        int accel = c.getInt("border.accelerate-min", 30);
        int showdown = c.getInt("arena-showdown.teleport-min", 45);
        if (!(shrink <= accel && accel <= showdown)) {
            w.add("Kolejnosc faz granicy niespojna: shrink(" + shrink + ") <= accelerate(" + accel + ") <= teleport(" + showdown + ").");
        }

        String storage = c.getString("storage.type", "SQLITE");
        if (!storage.equalsIgnoreCase("SQLITE") && !storage.equalsIgnoreCase("MYSQL")) {
            w.add("storage.type='" + storage + "' (oczekiwane SQLITE lub MYSQL).");
        }
        return w;
    }
}
