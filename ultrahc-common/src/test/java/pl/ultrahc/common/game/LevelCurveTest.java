package pl.ultrahc.common.game;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testy krzywej poziomow — weryfikacja tabeli progow ze spec. */
class LevelCurveTest {

    private LevelCurve specCurve() {
        // LINEAR slope=2000, intercept=500 + override 0-2 (jak w domyslnym config.yml).
        return new LevelCurve(LevelCurve.Mode.LINEAR, 2000, 500, 750, 1.35,
                Map.of(0, 750L, 1, 2800L, 2, 4500L));
    }

    @Test
    void overridesMatchSpec() {
        LevelCurve c = specCurve();
        assertEquals(750, c.requiredForLevel(0));
        assertEquals(2800, c.requiredForLevel(1));
        assertEquals(4500, c.requiredForLevel(2));
    }

    @Test
    void linearTailMatchesSpec() {
        LevelCurve c = specCurve();
        assertEquals(6500, c.requiredForLevel(3));
        assertEquals(8500, c.requiredForLevel(4));
        assertEquals(10500, c.requiredForLevel(5));
        // Najwyzszy prog ze spec: 33 gwiazdka -> 66 500.
        assertEquals(66500, c.requiredForLevel(33));
    }

    @Test
    void geometricUsesFormula() {
        LevelCurve c = new LevelCurve(LevelCurve.Mode.GEOMETRIC, 2000, 500, 750, 2.0, Map.of());
        assertEquals(750, c.requiredForLevel(0));   // 750 * 2^0
        assertEquals(1500, c.requiredForLevel(1));  // 750 * 2^1
        assertEquals(3000, c.requiredForLevel(2));  // 750 * 2^2
    }
}
