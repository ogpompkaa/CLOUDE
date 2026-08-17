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

        // Rola serwera.
        String role = c.getString("server.role", "LOBBY");
        if (!role.equalsIgnoreCase("LOBBY") && !role.equalsIgnoreCase("ARENA")) {
            w.add("server.role='" + role + "' (oczekiwane LOBBY lub ARENA).");
        }

        // Trudnosc swiata gry.
        String diff = c.getString("world.rules.difficulty", "HARD");
        if (!isOneOf(diff, "PEACEFUL", "EASY", "NORMAL", "HARD")) {
            w.add("world.rules.difficulty='" + diff + "' (oczekiwane PEACEFUL/EASY/NORMAL/HARD).");
        }

        // Zakresy liczbowe.
        if (c.getInt("world.pool-size", 1) < 1) w.add("world.pool-size musi byc >= 1.");
        if (c.getInt("game.spawn-attempts", 30) < 1) w.add("game.spawn-attempts musi byc >= 1.");
        if (c.getInt("party.max-size", 4) < 1) w.add("party.max-size musi byc >= 1.");
        if (c.getInt("effects.damage-numbers-ttl", 16) < 1) w.add("effects.damage-numbers-ttl musi byc >= 1.");
        int scr = c.getInt("world.rules.spawn-chunk-radius", 2);
        if (scr < 0 || scr > 32) w.add("world.rules.spawn-chunk-radius=" + scr + " (oczekiwane 0-32).");
        if (c.getBoolean("combat.old-pvp", true) && c.getDouble("combat.attack-speed", 1024) <= 0) {
            w.add("combat.attack-speed musi byc > 0 przy combat.old-pvp: true.");
        }

        // Krzywa poziomow.
        String levelsMode = c.getString("levels.mode", "LINEAR");
        if (!levelsMode.equalsIgnoreCase("LINEAR") && !levelsMode.equalsIgnoreCase("GEOMETRIC")) {
            w.add("levels.mode='" + levelsMode + "' (oczekiwane LINEAR lub GEOMETRIC).");
        }

        // Rangi: musi istniec grupa domyslna (pusty permission = GRACZ).
        var ranks = c.getConfigurationSection("ranks.groups");
        if (ranks != null && !ranks.getKeys(false).isEmpty()) {
            boolean hasDefault = false;
            for (String id : ranks.getKeys(false)) {
                String perm = c.getString("ranks.groups." + id + ".permission", "");
                if (perm == null || perm.isBlank()) { hasDefault = true; break; }
            }
            if (!hasDefault) w.add("ranks.groups: brak grupy domyslnej (z pustym 'permission') — nikt nie dostanie rangi GRACZ.");
        }

        // Format nicku musi zawierac %name%, inaczej nick zniknie.
        String nameFmt = c.getString("chat.name-format", "%name%");
        if (nameFmt != null && !nameFmt.contains("%name%")) {
            w.add("chat.name-format nie zawiera %name% — nick gracza sie nie pokaze.");
        }

        return w;
    }

    private static boolean isOneOf(String value, String... allowed) {
        if (value == null) return false;
        for (String a : allowed) if (a.equalsIgnoreCase(value.trim())) return true;
        return false;
    }
}
