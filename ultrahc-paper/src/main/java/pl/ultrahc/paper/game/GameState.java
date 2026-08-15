package pl.ultrahc.paper.game;

/** Stan pojedynczej instancji gry (cykl zycia). */
public enum GameState {
    /** Zbieranie graczy (przed osiagnieciem progu). */
    WAITING,
    /** Odliczanie 3 min po zebraniu progu graczy. */
    COUNTDOWN,
    /** Gra trwa. */
    RUNNING,
    /** Konczenie (ekran zwyciescy, sprzatanie swiata). */
    ENDING
}
