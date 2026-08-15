package pl.corekit.feature.back;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers each online player's previous location so {@code /back} can return
 * them to it. State is in-memory and cleared on quit — {@code /back} works
 * within a session, and the map never holds locations for offline players
 * (which would both leak memory and pin worlds).
 */
public final class BackService {

    private final Map<UUID, Location> previous = new ConcurrentHashMap<>();

    public void remember(UUID uuid, Location location) {
        previous.put(uuid, location.clone());
    }

    public Location previous(UUID uuid) {
        return previous.get(uuid);
    }

    public void clear(UUID uuid) {
        previous.remove(uuid);
    }
}
