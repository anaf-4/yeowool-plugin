package com.yeowool.life.dex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DexRewardRulesTest {

    private static final List<Integer> MILESTONES = List.of(25, 50, 75, 100);

    @Test
    void percentIsFlooredAndSafeForEmptyCategories() {
        assertEquals(57, DexRewardRules.percent(4, 7));
        assertEquals(100, DexRewardRules.percent(7, 7));
        assertEquals(99, DexRewardRules.percent(99, 100));
        assertEquals(0, DexRewardRules.percent(0, 0));
    }

    @Test
    void nextMilestoneIsTheFirstNotYetReached() {
        assertEquals(25, DexRewardRules.nextMilestone(0, MILESTONES));
        assertEquals(50, DexRewardRules.nextMilestone(25, MILESTONES));
        assertEquals(100, DexRewardRules.nextMilestone(99, MILESTONES));
        assertEquals(-1, DexRewardRules.nextMilestone(100, MILESTONES));
    }

    @Test
    void remainingForMatchesPercentThreshold() {
        assertEquals(1, DexRewardRules.remainingFor(50, 3, 7));
        assertEquals(0, DexRewardRules.remainingFor(50, 4, 7));
        assertEquals(2, DexRewardRules.remainingFor(100, 5, 7));
        assertEquals(1, DexRewardRules.remainingFor(25, 0, 3));
        for (int total = 1; total <= 40; total++) {
            for (int owned = 0; owned <= total; owned++) {
                for (int milestone : MILESTONES) {
                    boolean reached = DexRewardRules.percent(owned, total) >= milestone;
                    assertEquals(reached, DexRewardRules.remainingFor(milestone, owned, total) == 0,
                            "owned=" + owned + " total=" + total + " milestone=" + milestone);
                }
            }
        }
    }
}
