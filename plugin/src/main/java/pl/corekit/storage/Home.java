package pl.corekit.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * A named teleport point owned by a player. Stored as primitives (world name +
 * coordinates) rather than a Bukkit {@link Location} so it never pins a
 * {@link World} object and can be read entirely off the main thread.
 */
public record Home(
        UUID owner,
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public static Home of(UUID owner, String name, Location location) {
        return new Home(
                owner,
                name,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /**
     * Resolves this home to a live {@link Location}. Must be called on the main
     * thread (it touches the world registry). Returns {@code null} if the world
     * no longer exists — e.g. it was deleted or renamed since the home was set.
     */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
