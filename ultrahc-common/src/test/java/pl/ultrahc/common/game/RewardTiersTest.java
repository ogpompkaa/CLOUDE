package pl.ultrahc.common.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testy progow nagrod czasowych (stawki XP ze spec). */
class RewardTiersTest {

    private List<RewardTiers.Tier> xpTiers() {
        return List.of(
                new RewardTiers.Tier(5, 4.2),
                new RewardTiers.Tier(15, 5.8),
                new RewardTiers.Tier(30, 7.7),
                new RewardTiers.Tier(999, 9.3));
    }

    @Test
    void picksCorrectTierByMinute() {
        var t = xpTiers();
        assertEquals(4.2, RewardTiers.amountFor(t, 0));
        assertEquals(4.2, RewardTiers.amountFor(t, 4));
        assertEquals(5.8, RewardTiers.amountFor(t, 5));   // granica: 5 < 5 falsz -> nastepny prog
        assertEquals(5.8, RewardTiers.amountFor(t, 14));
        assertEquals(7.7, RewardTiers.amountFor(t, 15));
        assertEquals(7.7, RewardTiers.amountFor(t, 29));
        assertEquals(9.3, RewardTiers.amountFor(t, 30));  // powyzej 30 min
        assertEquals(9.3, RewardTiers.amountFor(t, 120));
    }

    @Test
    void emptyReturnsZero() {
        assertEquals(0, RewardTiers.amountFor(List.of(), 10));
    }
}
