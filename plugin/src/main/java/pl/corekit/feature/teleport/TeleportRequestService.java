package pl.corekit.feature.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.corekit.CoreKitPlugin;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.feature.teleport.TeleportRequest.Direction;
import pl.corekit.lang.MessageService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages {@code /tpa} / {@code /tpahere} requests and their lifecycle.
 *
 * <p>Requests are keyed by recipient — at most one pending request per player;
 * a new one supersedes the old (cancelling its expiry). Each request auto-expires
 * after {@code teleport.request-expiry-seconds}, and is cleaned up if either
 * party disconnects. Accepting hands the actual teleport to
 * {@link TeleportService}, so the warm-up and cooldown apply uniformly.
 */
public final class TeleportRequestService {

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final MessageService messages;
    private final TeleportService teleport;

    private final Map<UUID, TeleportRequest> byRecipient = new ConcurrentHashMap<>();

    public TeleportRequestService(CoreKitPlugin plugin, FeedbackService feedback, TeleportService teleport) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.messages = feedback.messages();
        this.teleport = teleport;
    }

    public void send(Player sender, Player recipient, Direction direction) {
        if (sender.equals(recipient)) {
            feedback.error(sender, "tpa.self");
            return;
        }
        UUID recipientId = recipient.getUniqueId();
        TeleportRequest previous = byRecipient.remove(recipientId);
        if (previous != null) {
            previous.expiryTask().cancel();
        }

        int expiry = plugin.configManager().settings().requestExpirySeconds();
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> expire(recipientId), expiry * 20L);
        byRecipient.put(recipientId, new TeleportRequest(sender.getUniqueId(), recipientId, direction, task));

        feedback.success(sender, "tpa.sent", MessageService.placeholder("target", recipient.getName()));
        notifyRecipient(recipient, sender.getName(), direction, expiry);
    }

    private void notifyRecipient(Player recipient, String senderName, Direction direction, int expiry) {
        String key = direction == Direction.GO ? "tpa.received-go" : "tpa.received-summon";
        recipient.sendMessage(messages.renderPrefixed(key, MessageService.placeholder("sender", senderName)));

        Component accept = messages.render("tpa.button.accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + senderName))
                .hoverEvent(HoverEvent.showText(messages.render("tpa.button.accept-hover")));
        Component deny = messages.render("tpa.button.deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + senderName))
                .hoverEvent(HoverEvent.showText(messages.render("tpa.button.deny-hover")));

        recipient.sendMessage(messages.render("tpa.prompt",
                        MessageService.placeholder("seconds", String.valueOf(expiry)))
                .append(Component.space()).append(accept)
                .append(Component.space()).append(deny));
        feedback.play(recipient, Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
    }

    public void accept(Player recipient, String expectedSender) {
        TeleportRequest request = byRecipient.get(recipient.getUniqueId());
        if (request == null) {
            feedback.error(recipient, "tpa.none");
            return;
        }
        Player sender = plugin.getServer().getPlayer(request.sender());
        if (sender == null) {
            clear(recipient.getUniqueId());
            feedback.error(recipient, "tpa.offline");
            return;
        }
        if (expectedSender != null && !sender.getName().equalsIgnoreCase(expectedSender)) {
            feedback.error(recipient, "tpa.no-such", MessageService.placeholder("player", expectedSender));
            return;
        }

        clear(recipient.getUniqueId());
        feedback.success(recipient, "tpa.accepted-self",
                MessageService.placeholder("player", sender.getName()));
        feedback.success(sender, "tpa.accepted-other",
                MessageService.placeholder("player", recipient.getName()));

        if (request.direction() == Direction.GO) {
            teleport.request(sender, recipient.getLocation(), recipient.getName());
        } else {
            teleport.request(recipient, sender.getLocation(), sender.getName());
        }
    }

    public void deny(Player recipient, String expectedSender) {
        TeleportRequest request = byRecipient.get(recipient.getUniqueId());
        if (request == null) {
            feedback.error(recipient, "tpa.none");
            return;
        }
        Player sender = plugin.getServer().getPlayer(request.sender());
        if (expectedSender != null && sender != null
                && !sender.getName().equalsIgnoreCase(expectedSender)) {
            feedback.error(recipient, "tpa.no-such", MessageService.placeholder("player", expectedSender));
            return;
        }

        clear(recipient.getUniqueId());
        feedback.success(recipient, "tpa.denied-self");
        if (sender != null) {
            feedback.error(sender, "tpa.denied-other",
                    MessageService.placeholder("player", recipient.getName()));
        }
    }

    /** The name of the player who has a pending request to {@code recipient}, or {@code null}. */
    public String pendingSenderName(UUID recipient) {
        TeleportRequest request = byRecipient.get(recipient);
        if (request == null) {
            return null;
        }
        Player sender = plugin.getServer().getPlayer(request.sender());
        return sender == null ? null : sender.getName();
    }

    private void expire(UUID recipientId) {
        TeleportRequest request = byRecipient.remove(recipientId);
        if (request == null) {
            return;
        }
        Player recipient = plugin.getServer().getPlayer(recipientId);
        Player sender = plugin.getServer().getPlayer(request.sender());
        if (recipient != null) {
            feedback.error(recipient, "tpa.expired");
        }
        if (sender != null) {
            feedback.error(sender, "tpa.expired");
        }
    }

    /** Removes any requests involving a disconnecting player. */
    public void handleQuit(UUID uuid) {
        TeleportRequest asRecipient = byRecipient.remove(uuid);
        if (asRecipient != null) {
            asRecipient.expiryTask().cancel();
        }
        byRecipient.values().removeIf(request -> {
            if (request.sender().equals(uuid)) {
                request.expiryTask().cancel();
                return true;
            }
            return false;
        });
    }

    public void cancelAll() {
        byRecipient.values().forEach(request -> request.expiryTask().cancel());
        byRecipient.clear();
    }

    private void clear(UUID recipientId) {
        TeleportRequest request = byRecipient.remove(recipientId);
        if (request != null) {
            request.expiryTask().cancel();
        }
    }
}
