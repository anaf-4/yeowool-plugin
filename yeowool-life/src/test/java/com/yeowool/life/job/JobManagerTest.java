package com.yeowool.life.job;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers only {@link JobManager#requiredXpForLevel(int)}, the one pure-math
 * method on this class — everything else touches Bukkit's scheduler/plugin
 * APIs and would need a running server (or heavier mocking) to exercise.
 * The constructor itself does no Bukkit calls, so passing nulls for the
 * plugin/repository/executor it never uses here is safe.
 */
class JobManagerTest {

    private JobManager manager(double base, double exponent) {
        return new JobManager(null, null, null, null, Map.of(), Map.of(), base, exponent, 50, 0);
    }

    @Test
    void levelOneMatchesBaseExactly() {
        JobManager manager = manager(100, 1.8);
        assertEquals(100, manager.requiredXpForLevel(1));
    }

    @Test
    void requiredXpGrowsWithLevel() {
        JobManager manager = manager(100, 1.8);
        long level1 = manager.requiredXpForLevel(1);
        long level10 = manager.requiredXpForLevel(10);
        long level30 = manager.requiredXpForLevel(30);
        assertTrue(level1 < level10, "higher level must require more xp");
        assertTrue(level10 < level30, "curve must keep increasing, not plateau or invert");
    }

    @Test
    void matchesTheFormulaDirectly() {
        JobManager manager = manager(100, 1.8);
        for (int level = 1; level <= 50; level++) {
            long expected = Math.round(100 * Math.pow(level, 1.8));
            assertEquals(expected, manager.requiredXpForLevel(level));
        }
    }
}
