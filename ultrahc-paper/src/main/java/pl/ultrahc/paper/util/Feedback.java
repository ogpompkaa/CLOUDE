package pl.ultrahc.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Pomocnicze efekty dla graczy: tytuly na srodku ekranu i dzwieki. */
public final class Feedback {

    private Feedback() {}

    public static void title(Player player, Component main, Component sub) {
        player.showTitle(Title.title(main, sub,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
    }

    public static void sound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    // Skroty na typowe zdarzenia.
    public static void levelUp(Player p) { sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f); }
    public static void buy(Player p) { sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f); }
    public static void kill(Player p) { sound(p, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f); }
    public static void win(Player p) { sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f); }
    public static void error(Player p) { sound(p, Sound.ENTITY_VILLAGER_NO, 1.0f); }
}
