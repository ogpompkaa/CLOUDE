package pl.ultrahc.paper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testy walidatora config.yml (bez serwera — konfiguracja w pamieci). */
class ConfigValidatorTest {

    /** Spojny config bazowy — pojedyncze testy psuja po jednej wartosci. */
    private YamlConfiguration sane() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("server.role", "ARENA");
        c.set("game.team-size", 1);
        c.set("game.min-players-to-countdown", 1);
        c.set("game.max-players", 100);
        c.set("game.countdown-seconds", 10);
        c.set("game.no-pvp-seconds", 30);
        c.set("game.spawn-attempts", 30);
        c.set("border.start", 1000);
        c.set("border.blocks-per-min", 24);
        c.set("border.blocks-per-min-fast", 30);
        c.set("border.shrink-start-min", 10);
        c.set("border.accelerate-min", 30);
        c.set("arena-showdown.teleport-min", 45);
        c.set("storage.type", "SQLITE");
        c.set("world.pool-size", 1);
        c.set("world.rules.difficulty", "HARD");
        c.set("world.rules.spawn-chunk-radius", 2);
        c.set("party.max-size", 4);
        c.set("effects.damage-numbers-ttl", 16);
        c.set("combat.old-pvp", true);
        c.set("combat.attack-speed", 1024);
        c.set("levels.mode", "LINEAR");
        c.set("ranks.groups.vip.permission", "ultrahc.rank.vip");
        c.set("ranks.groups.gracz.permission", ""); // grupa domyslna
        c.set("chat.name-format", "%group%%name% %level%");
        return c;
    }

    private boolean anyContains(List<String> w, String needle) {
        return w.stream().anyMatch(s -> s.contains(needle));
    }

    @Test
    void saneConfigHasNoWarnings() {
        assertTrue(ConfigValidator.validate(sane()).isEmpty(),
                "spojny config nie powinien dawac ostrzezen");
    }

    @Test
    void badRoleWarns() {
        YamlConfiguration c = sane();
        c.set("server.role", "HUB");
        assertTrue(anyContains(ConfigValidator.validate(c), "server.role"));
    }

    @Test
    void badDifficultyWarns() {
        YamlConfiguration c = sane();
        c.set("world.rules.difficulty", "SUPERHARD");
        assertTrue(anyContains(ConfigValidator.validate(c), "difficulty"));
    }

    @Test
    void teamSizeOutOfRangeWarns() {
        YamlConfiguration c = sane();
        c.set("game.team-size", 7);
        assertTrue(anyContains(ConfigValidator.validate(c), "team-size"));
    }

    @Test
    void phaseOrderWarns() {
        YamlConfiguration c = sane();
        c.set("border.accelerate-min", 5); // accelerate < shrink
        assertTrue(anyContains(ConfigValidator.validate(c), "faz granicy"));
    }

    @Test
    void missingDefaultRankWarns() {
        YamlConfiguration c = sane();
        c.set("ranks.groups.gracz.permission", "ultrahc.rank.gracz"); // juz nie domyslna
        assertTrue(anyContains(ConfigValidator.validate(c), "grupy domyslnej"));
    }

    @Test
    void nameFormatWithoutNameWarns() {
        YamlConfiguration c = sane();
        c.set("chat.name-format", "%group% %level%");
        assertTrue(anyContains(ConfigValidator.validate(c), "%name%"));
    }

    @Test
    void spawnChunkRadiusOutOfRangeWarns() {
        YamlConfiguration c = sane();
        c.set("world.rules.spawn-chunk-radius", 99);
        assertTrue(anyContains(ConfigValidator.validate(c), "spawn-chunk-radius"));
    }

    @Test
    void attackSpeedZeroWithOldPvpWarns() {
        YamlConfiguration c = sane();
        c.set("combat.attack-speed", 0);
        assertTrue(anyContains(ConfigValidator.validate(c), "attack-speed"));
    }

    @Test
    void minGreaterThanMaxWarns() {
        YamlConfiguration c = sane();
        c.set("game.min-players-to-countdown", 200);
        assertTrue(anyContains(ConfigValidator.validate(c), "min-players-to-countdown"));
    }
}
