package pl.ultrahc.paper;

/** Rola instancji serwera. Decyduje, ktore managery wstaja (patrz DECYZJE, sekcja 1). */
public enum ServerRole {
    /** Lobby: NPC, sklep, klasy, topki/hologramy, questy, sezon, wybor areny. */
    LOBBY,
    /** Arena: cykl gry, granica, dropy, druzyny, scoreboard, kompas, glowki. */
    ARENA;

    public static ServerRole fromString(String s) {
        try {
            return valueOf(s == null ? "LOBBY" : s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LOBBY;
        }
    }
}
