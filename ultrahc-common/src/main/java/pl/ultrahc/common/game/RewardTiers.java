package pl.ultrahc.common.game;

import java.util.List;

/**
 * Czysta logika progow nagrod czasowych (bez zaleznosci od Bukkita — testowalna).
 * Stawka = pierwszy prog, dla ktorego minuty < to-min; poza zakresem — ostatni prog.
 */
public final class RewardTiers {

    private RewardTiers() {}

    public record Tier(int toMin, double amount) {}

    public static double amountFor(List<Tier> tiers, int minutes) {
        for (Tier t : tiers) {
            if (minutes < t.toMin()) return t.amount();
        }
        return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).amount();
    }
}
