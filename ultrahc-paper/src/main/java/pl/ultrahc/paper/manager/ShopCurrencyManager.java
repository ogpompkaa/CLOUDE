package pl.ultrahc.paper.manager;

import pl.ultrahc.common.model.PlayerProfile;

/**
 * Waluta sklepowa (spec: "XP") — za klasy i receptury.
 *
 * <p>SYSTEM ODRĘBNY od PD ({@link LevelsManager}) i od natywnego expa Minecrafta.
 * Nazwa wyswietlana w jednym miejscu: {@link #DISPLAY_NAME} (kod) oraz klucz
 * messages.yml {@code currency.name}. Zmiana nazwy = jedna edycja.
 */
public class ShopCurrencyManager {

    /** Kanoniczna nazwa waluty sklepowej po stronie kodu (DECYZJA: "XP"). */
    public static final String DISPLAY_NAME = "XP";

    public long getBalance(PlayerProfile profile) {
        return profile.getCredits();
    }

    public void add(PlayerProfile profile, long amount) {
        profile.setCredits(Math.max(0, profile.getCredits() + amount));
    }

    public boolean has(PlayerProfile profile, long amount) {
        return profile.getCredits() >= amount;
    }

    /** Pobiera walute jesli starczy; zwraca true przy sukcesie. */
    public boolean tryDeduct(PlayerProfile profile, long amount) {
        if (!has(profile, amount)) return false;
        profile.setCredits(profile.getCredits() - amount);
        return true;
    }
}
