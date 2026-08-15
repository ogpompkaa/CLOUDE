package pl.corekit.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable representation of a stored player profile. Timestamps are epoch
 * seconds; {@code playtimeSeconds} is the accumulated online time.
 */
public record PlayerProfile(
        UUID uuid,
        String name,
        long firstSeen,
        long lastSeen,
        long playtimeSeconds
) {

    public Instant firstSeenInstant() {
        return Instant.ofEpochSecond(firstSeen);
    }

    public Instant lastSeenInstant() {
        return Instant.ofEpochSecond(lastSeen);
    }

    public Duration playtime() {
        return Duration.ofSeconds(playtimeSeconds);
    }
}
