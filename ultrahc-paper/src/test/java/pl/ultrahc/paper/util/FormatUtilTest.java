package pl.ultrahc.paper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testy formaterow czasu i liczb (czysta logika, bez Bukkita). */
class FormatUtilTest {

    @Test
    void hmsFormatsHoursMinutesSeconds() {
        assertEquals("00:00:00", TimeUtil.hms(0));
        assertEquals("00:00:59", TimeUtil.hms(59));
        assertEquals("01:01:01", TimeUtil.hms(3661));
        assertEquals("00:00:00", TimeUtil.hms(-5)); // nie ujemne
    }

    @Test
    void msFormatsMinutesSeconds() {
        assertEquals("00:00", TimeUtil.ms(0));
        assertEquals("01:05", TimeUtil.ms(65));
        assertEquals("10:00", TimeUtil.ms(600));
    }

    @Test
    void oneDecimalCommaUsesPolishSeparator() {
        assertEquals("549,2", NumberUtil.oneDecimalComma(549.2));
        assertEquals("1000,0", NumberUtil.oneDecimalComma(1000.0));
    }
}
