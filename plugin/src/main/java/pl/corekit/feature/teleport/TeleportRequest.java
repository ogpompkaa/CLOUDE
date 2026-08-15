package pl.corekit.feature.teleport;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * A pending teleport request between two players.
 *
 * <p>{@link Direction#GO} is a {@code /tpa}: the {@code sender} wants to
 * teleport to the {@code recipient}. {@link Direction#SUMMON} is a
 * {@code /tpahere}: the {@code sender} wants the {@code recipient} to come to
 * them. Either way the {@code recipient} is the one who accepts or denies.
 *
 * <p>{@code expiryTask} is the scheduled auto-expire; it is cancelled when the
 * request is accepted, denied, or superseded.
 */
public record TeleportRequest(UUID sender, UUID recipient, Direction direction, BukkitTask expiryTask) {

    public enum Direction {
        /** {@code /tpa} — sender teleports to recipient. */
        GO,
        /** {@code /tpahere} — recipient teleports to sender. */
        SUMMON
    }
}
