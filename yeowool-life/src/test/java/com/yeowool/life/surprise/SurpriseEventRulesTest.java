package com.yeowool.life.surprise;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurpriseEventRulesTest {

    private static final List<SurpriseEventType> ALL = List.of(SurpriseEventType.values());

    @Test
    void pickTypeNeverRepeatsThePreviousWhenThereIsAChoice() {
        for (int seed = 0; seed < 200; seed++) {
            SurpriseEventType picked = SurpriseEventRules.pickType(new Random(seed), ALL, SurpriseEventType.CROP_DROP).orElseThrow();
            assertNotEquals(SurpriseEventType.CROP_DROP, picked);
        }
    }

    @Test
    void pickTypeWithSingleOptionOrNoPreviousStillPicks() {
        assertEquals(Optional.of(SurpriseEventType.JOB_XP),
                SurpriseEventRules.pickType(new Random(1), List.of(SurpriseEventType.JOB_XP), SurpriseEventType.JOB_XP));
        assertTrue(SurpriseEventRules.pickType(new Random(1), ALL, null).isPresent());
        assertEquals(Optional.empty(), SurpriseEventRules.pickType(new Random(1), List.of(), null));
    }

    @Test
    void delayIsWithinBoundsInWholeMinutes() {
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            long delay = SurpriseEventRules.nextDelayMillis(random, 60, 120);
            assertTrue(delay >= 60 * 60_000L && delay <= 120 * 60_000L);
            assertEquals(0, delay % 60_000L);
        }
        assertEquals(60 * 60_000L, SurpriseEventRules.nextDelayMillis(new Random(1), 60, 10));
    }

    @Test
    void stillOursComparesWithTolerance() {
        assertTrue(SurpriseEventRules.stillOurs(2.0, 2.0));
        assertFalse(SurpriseEventRules.stillOurs(3.0, 2.0));
    }

    @Test
    void multipliersFormatWithoutTrailingZero() {
        assertEquals("2", SurpriseEventRules.formatMultiplier(2.0));
        assertEquals("1.5", SurpriseEventRules.formatMultiplier(1.5));
    }

    @Test
    void typesParseFromKeysLabelsAndAliases() {
        assertEquals(Optional.of(SurpriseEventType.TREASURE_DROP), SurpriseEventType.byKey("treasure_drop"));
        assertEquals(Optional.of(SurpriseEventType.CROP_DROP), SurpriseEventType.parse("작물"));
        assertEquals(Optional.of(SurpriseEventType.LAND_XP), SurpriseEventType.parse("토지경험치"));
        assertEquals(Optional.of(SurpriseEventType.JOB_XP), SurpriseEventType.parse("JOB_XP"));
        assertEquals(Optional.empty(), SurpriseEventType.parse("낚시"));
    }
}
