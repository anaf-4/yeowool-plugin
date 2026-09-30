package com.yeowool.market.casino;

import com.yeowool.market.casino.CasinoRules.DiceBet;
import com.yeowool.market.casino.CasinoRules.RouletteBet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoRulesTest {

    private static CasinoRules.Slot shippedSlot() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(CasinoRulesTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        return CasinoRules.Slot.fromConfig(Objects.requireNonNull(config.getConfigurationSection("casino.games.slot")));
    }

    // ---- 슬롯머신 ----

    @Test
    void shippedSlotTableIsAbout95Percent() {
        assertEquals(0.95, shippedSlot().expectedRtp(), 0.02);
    }

    @Test
    void slotSimulationOf100kSpinsStaysWithin2PercentOfTarget() {
        CasinoRules.Slot slot = shippedSlot();
        Random random = new Random(20260930);
        long bet = 100;
        long paid = 0;
        int spins = 100_000;
        for (int i = 0; i < spins; i++) {
            paid += CasinoRules.payout(bet, slot.multiplier(slot.spin(random)));
        }
        double rtp = paid / (double) (spins * bet);
        assertEquals(0.95, rtp, 0.02, "simulated RTP " + rtp);
    }

    @Test
    void slotPaysTriplesAndExactlyTwoCherries() {
        CasinoRules.Slot slot = shippedSlot();
        double cherryTriple = slot.symbols().get(0).payout();
        double sevenTriple = slot.symbols().get(slot.symbols().size() - 1).payout();
        int last = slot.symbols().size() - 1;
        assertEquals(cherryTriple, slot.multiplier(new int[]{0, 0, 0}));
        assertEquals(sevenTriple, slot.multiplier(new int[]{last, last, last}));
        assertEquals(slot.twoFirst(), slot.multiplier(new int[]{0, 3, 0}));
        assertEquals(slot.twoFirst(), slot.multiplier(new int[]{2, 0, 0}));
        assertEquals(0, slot.multiplier(new int[]{0, 1, 2}));
        assertEquals(0, slot.multiplier(new int[]{1, 1, 2}));
    }

    @Test
    void slotSpinOnlyReturnsValidSymbols() {
        CasinoRules.Slot slot = shippedSlot();
        Random random = new Random(1);
        for (int i = 0; i < 10_000; i++) {
            for (int reel : slot.spin(random)) {
                assertTrue(reel >= 0 && reel < slot.symbols().size());
            }
        }
    }

    // ---- 룰렛 ----

    @Test
    void zeroLosesEveryOutsideBetButWinsNumberZero() {
        for (RouletteBet bet : RouletteBet.values()) {
            if (bet != RouletteBet.NUMBER) {
                assertFalse(bet.wins(0, -1), bet.name());
            }
        }
        assertTrue(RouletteBet.NUMBER.wins(0, 0));
        assertFalse(RouletteBet.NUMBER.wins(0, 1));
    }

    @Test
    void rouletteOutsideBetsJudgeCorrectly() {
        assertTrue(RouletteBet.RED.wins(1, -1));
        assertTrue(RouletteBet.BLACK.wins(2, -1));
        assertTrue(RouletteBet.BLACK.wins(10, -1));
        assertTrue(RouletteBet.RED.wins(19, -1));
        assertTrue(RouletteBet.ODD.wins(35, -1));
        assertTrue(RouletteBet.EVEN.wins(36, -1));
        assertTrue(RouletteBet.LOW.wins(18, -1));
        assertTrue(RouletteBet.HIGH.wins(19, -1));
        assertTrue(RouletteBet.DOZEN1.wins(12, -1));
        assertTrue(RouletteBet.DOZEN2.wins(13, -1));
        assertTrue(RouletteBet.DOZEN2.wins(24, -1));
        assertTrue(RouletteBet.DOZEN3.wins(25, -1));
        assertFalse(RouletteBet.DOZEN1.wins(13, -1));
        assertTrue(RouletteBet.NUMBER.wins(17, 17));
    }

    @Test
    void everyRouletteBetReturns973Percent() {
        for (RouletteBet bet : RouletteBet.values()) {
            double multiplier = bet == RouletteBet.NUMBER ? 36 : bet.isDozen() ? 3 : 2;
            double returned = 0;
            for (int result = 0; result <= 36; result++) {
                returned += bet.wins(result, 7) ? multiplier : 0;
            }
            assertEquals(36.0 / 37, returned / 37, 1e-9, bet.name());
        }
        long reds = java.util.stream.IntStream.rangeClosed(1, 36).filter(CasinoRules::isRed).count();
        assertEquals(18, reds);
    }

    // ---- 주사위 ----

    @Test
    void diceJudgesLowSevenHigh() {
        for (int sum = 2; sum <= 12; sum++) {
            assertEquals(sum <= 6, DiceBet.LOW.wins(sum), "low " + sum);
            assertEquals(sum == 7, DiceBet.SEVEN.wins(sum), "seven " + sum);
            assertEquals(sum >= 8, DiceBet.HIGH.wins(sum), "high " + sum);
        }
    }

    // ---- 블랙잭 ----

    /** Card with the given rank (1 = A … 13 = K); {@code suit} keeps duplicates distinct. */
    private static int card(int rank, int suit) {
        return suit * 13 + rank - 1;
    }

    private static int card(int rank) {
        return card(rank, 0);
    }

    @Test
    void acesCountAsOneOrEleven() {
        assertEquals(21, CasinoRules.handValue(List.of(card(1), card(13))));
        assertEquals(12, CasinoRules.handValue(List.of(card(1), card(1, 1))));
        assertEquals(21, CasinoRules.handValue(List.of(card(1), card(1, 1), card(9))));
        assertEquals(16, CasinoRules.handValue(List.of(card(1), card(5), card(13))));
        assertEquals(22, CasinoRules.handValue(List.of(card(13), card(12), card(2))));
        assertEquals(20, CasinoRules.handValue(List.of(card(11), card(12))));
    }

    @Test
    void blackjackNeedsExactlyTwoCards() {
        assertTrue(CasinoRules.isBlackjack(List.of(card(1), card(10))));
        assertFalse(CasinoRules.isBlackjack(List.of(card(1), card(5), card(5, 1))));
    }

    @Test
    void dealerStandsOnAll17s() {
        assertTrue(CasinoRules.dealerHits(List.of(card(10), card(6))));
        assertFalse(CasinoRules.dealerHits(List.of(card(10), card(7))));
        assertFalse(CasinoRules.dealerHits(List.of(card(1), card(6)))); // soft 17
        assertTrue(CasinoRules.dealerHits(List.of(card(1), card(5))));  // soft 16
    }

    @Test
    void blackjackPayouts() {
        List<Integer> natural = List.of(card(1), card(13));
        List<Integer> twenty = List.of(card(10), card(12));
        List<Integer> nineteen = List.of(card(10), card(9));
        List<Integer> threeCard21 = List.of(card(7), card(7, 1), card(7, 2));
        List<Integer> bust = List.of(card(10), card(6), card(9));
        List<Integer> dealerBust = List.of(card(10, 1), card(6, 1), card(8, 1));
        assertEquals(25, CasinoRules.blackjackPayout(natural, twenty, 10, 2.5));
        assertEquals(10, CasinoRules.blackjackPayout(natural, List.of(card(1, 1), card(11)), 10, 2.5));
        assertEquals(0, CasinoRules.blackjackPayout(threeCard21, natural, 10, 2.5));
        assertEquals(0, CasinoRules.blackjackPayout(bust, dealerBust, 10, 2.5));
        assertEquals(20, CasinoRules.blackjackPayout(nineteen, dealerBust, 10, 2.5));
        assertEquals(20, CasinoRules.blackjackPayout(twenty, nineteen, 10, 2.5));
        assertEquals(10, CasinoRules.blackjackPayout(twenty, List.of(card(10, 1), card(13, 1)), 10, 2.5));
        assertEquals(0, CasinoRules.blackjackPayout(nineteen, twenty, 10, 2.5));
        assertEquals(40, CasinoRules.blackjackPayout(twenty, nineteen, 20, 2.5)); // doubled stake
        assertEquals(2, CasinoRules.blackjackPayout(natural, twenty, 1, 2.5));    // rounded down
    }

    // ---- 한도·공지 ----

    @Test
    void dailyBuyLimitRemaining() {
        assertEquals(500, CasinoRules.remainingBuy(0, 500));
        assertEquals(1, CasinoRules.remainingBuy(499, 500));
        assertEquals(0, CasinoRules.remainingBuy(500, 500));
        assertEquals(0, CasinoRules.remainingBuy(700, 500));
    }

    @Test
    void bigWinByMultiplierOrChips() {
        assertTrue(CasinoRules.bigWin(10, 200, 20, 1000));
        assertFalse(CasinoRules.bigWin(10, 190, 20, 1000));
        assertTrue(CasinoRules.bigWin(100, 1000, 20, 1000));
        assertFalse(CasinoRules.bigWin(100, 0, 20, 1000));
    }
}
