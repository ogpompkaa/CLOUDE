package pl.ultrahc.common.game;

import java.util.Map;

/**
 * Czysta logika progow poziomow (bez zaleznosci od Bukkita — testowalna).
 * Prog = tablica override (poziomy specjalne, np. 0-2 ze spec) albo wzor
 * parametryzowany (LINEAR/GEOMETRIC). Patrz DECYZJE, sekcja 3.
 */
public class LevelCurve {

    public enum Mode { LINEAR, GEOMETRIC }

    private final Mode mode;
    private final double slope;
    private final double intercept;
    private final double geomBase;
    private final double geomGrowth;
    private final Map<Integer, Long> overrides;

    public LevelCurve(Mode mode, double slope, double intercept,
                      double geomBase, double geomGrowth, Map<Integer, Long> overrides) {
        this.mode = mode;
        this.slope = slope;
        this.intercept = intercept;
        this.geomBase = geomBase;
        this.geomGrowth = geomGrowth;
        this.overrides = overrides;
    }

    /** Ile PD potrzeba, aby awansowac Z podanego poziomu na nastepny. */
    public long requiredForLevel(int level) {
        Long override = overrides.get(level);
        if (override != null) return override;
        if (mode == Mode.GEOMETRIC) {
            return Math.round(geomBase * Math.pow(geomGrowth, level));
        }
        return Math.round(slope * level + intercept);
    }
}
