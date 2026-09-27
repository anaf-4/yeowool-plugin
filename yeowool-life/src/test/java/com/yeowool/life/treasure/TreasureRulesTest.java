package com.yeowool.life.treasure;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasureRulesTest {

    @Test
    void directionUsesMinecraftCompass() {
        assertEquals("북쪽", TreasureRules.direction(0, -10));
        assertEquals("동쪽", TreasureRules.direction(10, 0));
        assertEquals("남쪽", TreasureRules.direction(0, 10));
        assertEquals("서쪽", TreasureRules.direction(-10, 0));
        assertEquals("북동쪽", TreasureRules.direction(10, -10));
        assertEquals("남서쪽", TreasureRules.direction(-10, 10));
        assertEquals("북서쪽", TreasureRules.direction(-10, -10));
        assertEquals("남동쪽", TreasureRules.direction(10, 10));
    }

    @Test
    void roughDistanceIsCoarserFarAway() {
        assertEquals(350, TreasureRules.roughDistance(362));
        assertEquals(1000, TreasureRules.roughDistance(987));
        assertEquals(130, TreasureRules.roughDistance(127));
        assertEquals(10, TreasureRules.roughDistance(3));
    }

    @Test
    void randomSpotStaysInsideTheRing() {
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            int[] spot = TreasureRules.randomSpot(random, 100, -200, 500, 3000);
            double distance = Math.hypot(spot[0] - 100, spot[1] + 200);
            assertTrue(distance >= 499 && distance <= 3001, "distance " + distance);
        }
    }

    @Test
    void pickTierFollowsWeights() {
        Map<TreasureTier, Integer> onlyRare = new EnumMap<>(TreasureTier.class);
        onlyRare.put(TreasureTier.COMMON, 0);
        onlyRare.put(TreasureTier.RARE, 5);
        onlyRare.put(TreasureTier.LEGENDARY, 0);
        for (int i = 0; i < 100; i++) {
            assertEquals(TreasureTier.RARE, TreasureRules.pickTier(new Random(i), onlyRare));
        }
        assertEquals(TreasureTier.COMMON, TreasureRules.pickTier(new Random(1), new EnumMap<>(TreasureTier.class)));
    }

    @Test
    void bandIsThe200BlockCellContainingTheCoordinate() {
        assertEquals("1200~1400", TreasureRules.band(1234));
        assertEquals("-200~0", TreasureRules.band(-1));
        assertEquals("0~200", TreasureRules.band(0));
    }

    @Test
    void pickItemsReturnsDistinctElementsUpToCount() {
        List<String> pool = List.of("a", "b", "c");
        List<String> picked = TreasureRules.pickItems(pool, 2, new Random(3));
        assertEquals(2, picked.size());
        assertTrue(pool.containsAll(picked));
        assertTrue(!picked.get(0).equals(picked.get(1)));
        assertEquals(3, TreasureRules.pickItems(pool, 10, new Random(3)).size());
        assertEquals(0, TreasureRules.pickItems(List.of(), 2, new Random(3)).size());
    }

    @Test
    void randomMoneyStaysInRange() {
        Random random = new Random(11);
        for (int i = 0; i < 1000; i++) {
            long money = TreasureRules.randomMoney(random, 1000, 5000);
            assertTrue(money >= 1000 && money <= 5000, "money " + money);
        }
        assertEquals(700, TreasureRules.randomMoney(random, 700, 100));
    }

    @Test
    void tierParsesKeysLabelsAndNames() {
        assertEquals(Optional.of(TreasureTier.LEGENDARY), TreasureTier.parse("전설"));
        assertEquals(Optional.of(TreasureTier.RARE), TreasureTier.parse("rare"));
        assertEquals(Optional.of(TreasureTier.COMMON), TreasureTier.parse("COMMON"));
        assertEquals(Optional.empty(), TreasureTier.parse("신화"));
    }
}
