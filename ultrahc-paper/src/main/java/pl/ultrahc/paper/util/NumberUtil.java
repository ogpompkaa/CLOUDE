package pl.ultrahc.paper.util;

/** Formatowanie liczb w stylu polskim (przecinek dziesietny). */
public final class NumberUtil {

    private NumberUtil() {}

    /** Jedno miejsce po przecinku, separator "," (zlota liczba granicy: np. 549,2). */
    public static String oneDecimalComma(double value) {
        return String.format("%.1f", value).replace('.', ',');
    }

    /** Liczba z separatorem tysiecy (spacja, styl PL): np. 21 750. */
    public static String grouped(long value) {
        return String.format("%,d", value).replace(',', ' ');
    }
}
