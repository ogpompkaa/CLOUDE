package pl.corekit.storage;

import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Async data-access for {@link Home}s. Home names are stored lower-cased so that
 * "Base" and "base" refer to the same point; callers pass whatever the player
 * typed and this class canonicalises.
 */
public final class HomeRepository {

    private final DatabaseManager database;

    public HomeRepository(DatabaseManager database) {
        this.database = database;
    }

    private static String canonical(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Creates or moves a named home in a single atomic UPSERT. */
    public CompletableFuture<Void> save(UUID owner, String name, Location location) {
        String canonical = canonical(name);
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO homes (uuid, name, world, x, y, z, yaw, pitch)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid, name) DO UPDATE SET
                        world = excluded.world,
                        x = excluded.x, y = excluded.y, z = excluded.z,
                        yaw = excluded.yaw, pitch = excluded.pitch""")) {
                statement.setString(1, owner.toString());
                statement.setString(2, canonical);
                statement.setString(3, location.getWorld().getName());
                statement.setDouble(4, location.getX());
                statement.setDouble(5, location.getY());
                statement.setDouble(6, location.getZ());
                statement.setFloat(7, location.getYaw());
                statement.setFloat(8, location.getPitch());
                statement.executeUpdate();
            }
        });
    }

    /** @return {@code true} if a home was actually removed. */
    public CompletableFuture<Boolean> delete(UUID owner, String name) {
        String canonical = canonical(name);
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM homes WHERE uuid = ? AND name = ?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, canonical);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Optional<Home>> find(UUID owner, String name) {
        String canonical = canonical(name);
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM homes WHERE uuid = ? AND name = ?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, canonical);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<Home>empty();
                }
            }
        });
    }

    public CompletableFuture<List<Home>> findAll(UUID owner) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM homes WHERE uuid = ? ORDER BY name")) {
                statement.setString(1, owner.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    List<Home> homes = new ArrayList<>();
                    while (rs.next()) {
                        homes.add(map(rs));
                    }
                    return homes;
                }
            }
        });
    }

    private static Home map(ResultSet rs) throws java.sql.SQLException {
        return new Home(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"));
    }
}
