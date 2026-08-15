package pl.ultrahc.paper.util;

/** Formatowanie czasu. */
public final class TimeUtil {

    private TimeUtil() {}

    /** Sekundy -> HH:MM:SS (timer scoreboardu). */
    public static String hms(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
