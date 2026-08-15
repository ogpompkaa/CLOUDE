package pl.ultrahc.common.game;

/**
 * Czysta matematyka kurczenia granicy (bez zaleznosci od Bukkita — testowalna).
 * Trzy fazy: staly rozmiar do shrinkStart, tempo perMin do accelMin, potem
 * przyspieszone perMinFast. Patrz DECYZJE, sekcja 2.
 */
public class BorderCurve {

    private final double start;
    private final int shrinkStartMin;
    private final double perMin;
    private final int accelerateMin;
    private final double perMinFast;

    public BorderCurve(double start, int shrinkStartMin, double perMin, int accelerateMin, double perMinFast) {
        this.start = start;
        this.shrinkStartMin = shrinkStartMin;
        this.perMin = perMin;
        this.accelerateMin = accelerateMin;
        this.perMinFast = perMinFast;
    }

    /** Rozmiar granicy (srednica) w podanej minucie gry. */
    public double sizeAt(int minute) {
        if (minute <= shrinkStartMin) return start;
        if (minute <= accelerateMin) {
            return Math.max(1, start - perMin * (minute - shrinkStartMin));
        }
        double atAccelerate = start - perMin * (accelerateMin - shrinkStartMin);
        return Math.max(1, atAccelerate - perMinFast * (minute - accelerateMin));
    }
}
