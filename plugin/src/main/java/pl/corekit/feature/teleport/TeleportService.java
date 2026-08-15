package pl.corekit.feature.teleport;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.corekit.CoreKitPlugin;
import pl.corekit.config.Settings;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.lang.MessageService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds a "premium" teleport flow to {@code /home} and {@code /spawn}:
 * a boss-bar warm-up countdown, cancellation on movement/damage, and a
 * post-teleport cooldown — all configurable, with per-player bypass permissions.
 *
 * <p>Warm-ups and cooldowns are the two mechanics that make teleport commands
 * feel deliberate rather than instant-cheat; both are opt-out via config
 * ({@code warmup-seconds: 0}, {@code cooldown-seconds: 0}) and permission
 * ({@code corekit.teleport.bypass-warmup/-cooldown}).
 */
public final class TeleportService {

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final MessageService messages;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public TeleportService(CoreKitPlugin plugin, FeedbackService feedback, MessageService messages) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.messages = messages;
    }

    /**
     * Begins a teleport for {@code player} to {@code destination}. Honours
     * cooldown, then either teleports immediately or starts a warm-up.
     * {@code label} names the destination for the countdown message.
     */
    public void request(Player player, Location destination, String label) {
        Settings settings = plugin.configManager().settings();
        UUID id = player.getUniqueId();

        if (settings.cooldownSeconds() > 0 && !player.hasPermission("corekit.teleport.bypass-cooldown")) {
            long remainingMillis = cooldownUntil.getOrDefault(id, 0L) - System.currentTimeMillis();
            if (remainingMillis > 0) {
                long seconds = (remainingMillis + 999) / 1000;
                feedback.error(player, "teleport.cooldown",
                        MessageService.placeholder("time", seconds + "s"));
                return;
            }
        }

        if (settings.warmupSeconds() <= 0 || player.hasPermission("corekit.teleport.bypass-warmup")) {
            complete(player, destination);
            return;
        }

        startWarmup(player, destination, label, settings.warmupSeconds());
    }

    private void startWarmup(Player player, Location destination, String label, int seconds) {
        UUID id = player.getUniqueId();
        cancel(id, false); // drop any previous pending teleport

        Location origin = player.getLocation();
        BossBar bar = BossBar.bossBar(
                warmupText(label, seconds), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        feedback.play(player, Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.2f);

        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    Pending finished = pending.remove(id);
                    if (finished != null) {
                        player.hideBossBar(finished.bar());
                    }
                    cancel();
                    complete(player, destination);
                    return;
                }
                bar.name(warmupText(label, remaining));
                bar.progress(Math.max(0f, Math.min(1f, (float) remaining / seconds)));
                feedback.play(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.5f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pending.put(id, new Pending(task, bar,
                origin.getWorld().getName(),
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()));
    }

    private void complete(Player player, Location destination) {
        Settings settings = plugin.configManager().settings();
        if (settings.cooldownSeconds() > 0 && !player.hasPermission("corekit.teleport.bypass-cooldown")) {
            cooldownUntil.put(player.getUniqueId(),
                    System.currentTimeMillis() + settings.cooldownSeconds() * 1000L);
        }
        feedback.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        player.teleportAsync(destination);
    }

    /** @return {@code true} if the player moved off the block they started on. */
    public boolean hasMoved(UUID id, Location to) {
        Pending p = pending.get(id);
        if (p == null || to == null) {
            return false;
        }
        return !p.world().equals(to.getWorld().getName())
                || p.x() != to.getBlockX()
                || p.y() != to.getBlockY()
                || p.z() != to.getBlockZ();
    }

    public boolean isPending(UUID id) {
        return pending.containsKey(id);
    }

    /** Cancels a pending warm-up, optionally telling the player it was cancelled. */
    public void cancel(UUID id, boolean notify) {
        Pending p = pending.remove(id);
        if (p == null) {
            return;
        }
        p.task().cancel();
        Player player = plugin.getServer().getPlayer(id);
        if (player != null) {
            player.hideBossBar(p.bar());
            if (notify) {
                feedback.error(player, "teleport.cancelled");
            }
        }
    }

    /** Clears every pending teleport (called on plugin disable). */
    public void cancelAll() {
        pending.forEach((id, p) -> {
            p.task().cancel();
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.hideBossBar(p.bar());
            }
        });
        pending.clear();
    }

    public void forget(UUID id) {
        cancel(id, false);
        cooldownUntil.remove(id);
    }

    private net.kyori.adventure.text.Component warmupText(String label, int seconds) {
        return messages.render("teleport.warmup",
                MessageService.placeholder("name", label),
                MessageService.placeholder("seconds", String.valueOf(seconds)));
    }

    /** In-flight warm-up state: the countdown task, its boss bar, and origin block. */
    private record Pending(BukkitTask task, BossBar bar, String world, int x, int y, int z) {
    }
}
