package pl.corekit.feature.god;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players currently have god mode (damage immunity) enabled.
 *
 * <p>State is in-memory and intentionally not persisted — god mode is a
 * transient staff/testing convenience, and it resets on restart like most
 * essentials implementations. The backing set is concurrent because it is read
 * from the damage event (main thread) and mutated from commands.
 */
public final class GodService {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    /** Toggles god mode and returns the new state. */
    public boolean toggle(UUID uuid) {
        if (enabled.add(uuid)) {
            return true;
        }
        enabled.remove(uuid);
        return false;
    }

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

    public void disable(UUID uuid) {
        enabled.remove(uuid);
    }
}
