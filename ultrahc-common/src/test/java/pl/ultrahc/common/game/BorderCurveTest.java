package pl.ultrahc.common.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testy matematyki granicy — sanity-check z DECYZJE (min29≈544, fazy). */
class BorderCurveTest {

    // start 1000, shrink od 10 min, 24/min, przyspieszenie od 30 min do 30/min.
    private BorderCurve curve() {
        return new BorderCurve(1000, 10, 24, 30, 30);
    }

    @Test
    void staticBeforeShrink() {
        assertEquals(1000, curve().sizeAt(5));
        assertEquals(1000, curve().sizeAt(10));
    }

    @Test
    void phase1MatchesReference() {
        // Punkt odniesienia ze zrzutu: min 29 ~ 544 (≈549 w grze).
        assertEquals(544, curve().sizeAt(29));
        assertEquals(520, curve().sizeAt(30));
    }

    @Test
    void phase2Accelerates() {
        // 520 przy min30, potem 30/min -> min45 = 520 - 30*15 = 70.
        assertEquals(70, curve().sizeAt(45));
    }
}
