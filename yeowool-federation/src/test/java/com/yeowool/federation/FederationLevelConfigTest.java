package com.yeowool.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederationLevelConfigTest {

    private final FederationLevelConfig config = new FederationLevelConfig(20000, 30000, 3, 1);

    @Test
    void nextLevelRequirementsScaleWithCurrentLevel() {
        assertEquals(20000, config.activityForNextLevel(1));
        assertEquals(30000, config.costForNextLevel(1));
        assertEquals(100000, config.activityForNextLevel(5));
        assertEquals(150000, config.costForNextLevel(5));
    }

    @Test
    void memberCapStartsAtBaseAndGrowsPerLevel() {
        assertEquals(3, config.memberCap(1));
        assertEquals(4, config.memberCap(2));
        assertEquals(7, config.memberCap(5));
    }

    @Test
    void veryHighLevelsDoNotOverflowIntoNegativeRequirements() {
        assertEquals(20000L * 1_000_000, config.activityForNextLevel(1_000_000));
        assertEquals(30000L * 1_000_000, config.costForNextLevel(1_000_000));
    }
}
