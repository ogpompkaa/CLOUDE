package pl.corekit.feature.feedback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.lang.MessageService;

/**
 * Unifies user feedback so every command reacts the same "premium" way:
 * a styled message plus an optional subtle sound, and a choice between chat and
 * the action bar for lightweight confirmations.
 *
 * <p>All toggles ({@code feedback.sounds}, {@code feedback.action-bar}) are read
 * live from config on each call, so {@code /corekit reload} takes effect without
 * a restart.
 */
public final class FeedbackService {

    private final CoreKitPlugin plugin;
    private final MessageService messages;

    public FeedbackService(CoreKitPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public MessageService messages() {
        return messages;
    }

    /** Positive outcome: prefixed chat line + a bright confirmation chime. */
    public void success(CommandSender recipient, String key, TagResolver... resolvers) {
        recipient.sendMessage(messages.renderPrefixed(key, resolvers));
        if (recipient instanceof Player player) {
            play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
    }

    /** Failure/denied: prefixed chat line + a low "nope" tone. */
    public void error(CommandSender recipient, String key, TagResolver... resolvers) {
        recipient.sendMessage(messages.renderPrefixed(key, resolvers));
        if (recipient instanceof Player player) {
            play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
        }
    }

    /**
     * Snappy confirmation for self-utility toggles (heal/feed/fly/god/gamemode).
     * Uses the action bar when enabled so quick actions don't clutter chat, with
     * a pitch that rises for "on" and falls for "off".
     */
    public void quick(Player player, boolean positive, String key, TagResolver... resolvers) {
        if (plugin.configManager().settings().actionBarEnabled()) {
            player.sendActionBar(messages.render(key, resolvers));
        } else {
            player.sendMessage(messages.renderPrefixed(key, resolvers));
        }
        play(player,
                positive ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HAT,
                0.6f, positive ? 1.6f : 1.0f);
    }

    /** Sends a raw, already-rendered component to chat (used for rich lists). */
    public void raw(CommandSender recipient, Component component) {
        recipient.sendMessage(component);
    }

    public void play(Player player, Sound sound, float volume, float pitch) {
        if (plugin.configManager().settings().soundsEnabled()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
