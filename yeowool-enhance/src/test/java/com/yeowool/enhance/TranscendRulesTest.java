package com.yeowool.enhance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscendRulesTest {

    @Test
    void levelCapPerStage() {
        assertEquals(30, TranscendRules.levelCap(0, 30));
        assertEquals(10, TranscendRules.levelCap(1, 30));
        assertEquals(20, TranscendRules.levelCap(2, 30));
        assertEquals(30, TranscendRules.levelCap(3, 30));
    }

    @Test
    void transcendOnlyAtEachGate() {
        assertTrue(TranscendRules.canTranscend(0, 30, 30));
        assertFalse(TranscendRules.canTranscend(0, 29, 30));
        assertTrue(TranscendRules.canTranscend(1, 10, 30));
        assertFalse(TranscendRules.canTranscend(1, 9, 30));
        assertTrue(TranscendRules.canTranscend(2, 20, 30));
        assertFalse(TranscendRules.canTranscend(3, 30, 30));
    }

    @Test
    void netheriteVariantOnlyForGearWithANetheriteVersion() {
        assertEquals("NETHERITE_SWORD", TranscendRules.netheriteVariant("DIAMOND_SWORD"));
        assertEquals("NETHERITE_PICKAXE", TranscendRules.netheriteVariant("WOODEN_PICKAXE"));
        assertEquals("NETHERITE_HELMET", TranscendRules.netheriteVariant("LEATHER_HELMET"));
        assertEquals("NETHERITE_BOOTS", TranscendRules.netheriteVariant("CHAINMAIL_BOOTS"));
        assertEquals("NETHERITE_HOE", TranscendRules.netheriteVariant("GOLDEN_HOE"));
        assertEquals("NETHERITE_AXE", TranscendRules.netheriteVariant("NETHERITE_AXE"));
        assertEquals("BOW", TranscendRules.netheriteVariant("BOW"));
        assertEquals("TURTLE_HELMET", TranscendRules.netheriteVariant("TURTLE_HELMET"));
        assertEquals("GOLDEN_APPLE", TranscendRules.netheriteVariant("GOLDEN_APPLE"));
    }

    @Test
    void bonusNeverDropsWhenTranscendingAndPeaksAtStageThree() {
        double beforeTranscend = TranscendRules.bonus(0, 30, 30, 0.4, 3.0, 0);
        double firstStageStart = TranscendRules.bonus(1, 0, 30, 0.4, 1.0, 3.2);
        double stageOneTop = TranscendRules.bonus(1, 10, 30, 0.4, 1.0, 3.2);
        double stageTwoStart = TranscendRules.bonus(2, 10, 30, 0.4, 1.0, 3.6);
        double max = TranscendRules.bonus(3, 30, 30, 0.4, 1.0, 4.2);
        assertEquals(36.0, beforeTranscend, 1e-9);
        assertEquals(38.4, firstStageStart, 1e-9);
        assertEquals(100.8, max, 1e-9);
        assertTrue(firstStageStart > beforeTranscend);
        assertTrue(stageTwoStart > stageOneTop);
    }
}
