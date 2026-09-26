package com.yeowool.market.questboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBoardRulesTest {

    @Test
    void totalRewardMultipliesAndFlagsOverflow() {
        assertEquals(6400, QuestBoardRules.totalReward(64, 100));
        assertEquals(-1, QuestBoardRules.totalReward(100_000, Long.MAX_VALUE / 2));
    }

    @Test
    void feeIsFloorOfPercent() {
        assertEquals(320, QuestBoardRules.fee(6400, 5));
        assertEquals(0, QuestBoardRules.fee(19, 5));
        assertEquals(5, QuestBoardRules.fee(101, 5));
        assertEquals(0, QuestBoardRules.fee(6400, 0));
        assertEquals(-1, QuestBoardRules.fee(Long.MAX_VALUE, 200));
    }

    @Test
    void upfrontCostIsTotalPlusFee() {
        assertEquals(6720, QuestBoardRules.upfrontCost(64, 100, 5));
        assertEquals(-1, QuestBoardRules.upfrontCost(2, Long.MAX_VALUE / 2, 5));
    }

    @Test
    void refundCoversOnlyUndeliveredItems() {
        assertEquals(3000, QuestBoardRules.refund(64, 34, 100));
        assertEquals(0, QuestBoardRules.refund(64, 64, 100));
        assertEquals(0, QuestBoardRules.refund(10, 12, 100));
    }

    @Test
    void deliverableIsCappedByRemainingAndHeld() {
        assertEquals(10, QuestBoardRules.deliverable(10, 64));
        assertEquals(5, QuestBoardRules.deliverable(64, 5));
        assertEquals(0, QuestBoardRules.deliverable(0, 5));
        assertEquals(0, QuestBoardRules.deliverable(-3, 5));
    }

    @Test
    void validQuantityRespectsBounds() {
        assertTrue(QuestBoardRules.validQuantity(1, 100));
        assertTrue(QuestBoardRules.validQuantity(100, 100));
        assertFalse(QuestBoardRules.validQuantity(0, 100));
        assertFalse(QuestBoardRules.validQuantity(101, 100));
    }
}
