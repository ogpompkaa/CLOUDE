package pl.corekit.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Data-access object for {@link PlayerProfile}s. Every method returns a
 * {@link CompletableFuture} that completes off the main thread; callers decide
 * whether to hop back on via {@link DatabaseManager#sync(Runnable)}.
 *
 * <p>This is the template a new feature clones: a thin, purpose-named class that
 * owns exactly one table's SQL and exposes intention-revealing methods rather
 * than leaking {@code Connection}s to the rest of the plugin.
 */
public final class PlayerProfileRepository {

    private final DatabaseManager database;

    public PlayerProfileRepository(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Inserts a profile on first join, or refreshes name and last-seen on
     * return, in a single atomic UPSERT.
     */
    public CompletableFuture<Void> recordJoin(UUID uuid, String name, long epochSeconds) {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_profiles (uuid, name, first_seen, last_seen)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        last_seen = excluded.last_seen""")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, epochSeconds);
                statement.setLong(4, epochSeconds);
                statement.executeUpdate();
            }
        });
    }

    /** Adds elapsed online time and updates the last-seen timestamp on quit. */
    public CompletableFuture<Void> recordQuit(UUID uuid, long sessionSeconds, long epochSeconds) {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_profiles
                    SET playtime_seconds = playtime_seconds + ?,
                        last_seen = ?
                    WHERE uuid = ?""")) {
                statement.setLong(1, Math.max(0, sessionSeconds));
                statement.setLong(2, epochSeconds);
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, name, first_seen, last_seen, playtime_seconds "
                            + "FROM player_profiles WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PlayerProfile(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getLong("first_seen"),
                            rs.getLong("last_seen"),
                            rs.getLong("playtime_seconds")));
                }
            }
        });
    }
}
