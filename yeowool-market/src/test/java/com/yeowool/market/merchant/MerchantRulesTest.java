package com.yeowool.market.merchant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantRulesTest {

    @Test
    void delayStaysWithinBoundsInWholeMinutes() {
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long delay = MerchantRules.nextDelayMillis(random, 180, 300);
            assertTrue(delay >= 180 * 60_000L && delay <= 300 * 60_000L, "out of range: " + delay);
            assertEquals(0, delay % 60_000L);
        }
    }

    @Test
    void delayUsesMinWhenBoundsAreInverted() {
        assertEquals(180 * 60_000L, MerchantRules.nextDelayMillis(new Random(1), 180, 100));
    }

    @Test
    void delayClampsNegativeMinToZero() {
        assertEquals(0, MerchantRules.nextDelayMillis(new Random(1), -5, -1));
    }

    @Test
    void pickReturnsEmptyForNoOptions() {
        assertEquals(Optional.empty(), MerchantRules.pick(List.of(), new Random(1)));
    }

    @Test
    void pickReturnsAnElementOfTheList() {
        List<String> spots = List.of("a", "b", "c");
        for (int i = 0; i < 50; i++) {
            assertTrue(spots.contains(MerchantRules.pick(spots, new Random(i)).orElseThrow()));
        }
    }
}
