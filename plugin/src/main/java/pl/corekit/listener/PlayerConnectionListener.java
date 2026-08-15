package pl.corekit.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.corekit.CoreKitPlugin;
import pl.corekit.lang.MessageService;
import pl.corekit.storage.PlayerProfileRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps {@code player_profiles} in sync with connection events and demonstrates
 * the intended I/O pattern: fire an async repository call on join/quit, never
 * blocking the main thread, and hop back onto it only to touch the Bukkit API.
 *
 * <p>Session start times are held in a concurrent map keyed by UUID. The map is
 * cleaned up on quit, so it cannot leak memory across reconnects.
 */
public final class PlayerConnectionListener implements Listener {

    private final CoreKitPlugin plugin;
    private final PlayerProfileRepository profiles;
    private final MessageService messages;

    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    public PlayerConnectionListener(CoreKitPlugin plugin,
                                    PlayerProfileRepository profiles,
                                    MessageService messages) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        long now = Instant.now().getEpochSecond();

        sessionStart.put(uuid, now);
        profiles.recordJoin(uuid, name, now).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to persist join for {}", name, throwable);
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        long now = Instant.now().getEpochSecond();

        Long start = sessionStart.remove(uuid);
        long sessionSeconds = start == null ? 0 : Math.max(0, now - start);

        profiles.recordQuit(uuid, sessionSeconds, now).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to persist quit for {}",
                    event.getPlayer().getName(), throwable);
            return null;
        });
    }
}
