package pl.ultrahc.common.instances;

/**
 * Migawka stanu jednej instancji gry (arena-serwera) w rejestrze sieciowym.
 * Id = nazwa serwera w Velocity (do transferu gracza).
 */
public record InstanceInfo(
        String id,
        String state,       // WAITING / COUNTDOWN / RUNNING / ENDING
        int players,
        int maxPlayers,
        int teamSize,
        String mode,        // SOLO / DUO / TRIO / SQUAD
        long heartbeat) {   // czas ostatniego odswiezenia (epoch millis)
}
