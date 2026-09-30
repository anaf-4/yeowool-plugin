package com.yeowool.enhance;

import com.yeowool.enhance.EnhanceAid.Fail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnhanceAidTest {

    @Test
    void boosterAddsToRateCappedAt100() {
        assertEquals(80.0, EnhanceAid.boostedRate(70.0, 10));
        assertEquals(100.0, EnhanceAid.boostedRate(90.0, 20));
        assertEquals(15.0, EnhanceAid.boostedRate(15.0, 0));
        assertEquals(15.0, EnhanceAid.boostedRate(15.0, -5));
    }

    @Test
    void failOutcomeByRoll() {
        // destroy 15%, downgrade 35%
        assertEquals(Fail.DESTROY, EnhanceAid.failOutcome(10, 15, 35, false, false));
        assertEquals(Fail.DOWNGRADE, EnhanceAid.failOutcome(30, 15, 35, false, false));
        assertEquals(Fail.SAFE, EnhanceAid.failOutcome(60, 15, 35, false, false));
        assertEquals(Fail.DOWNGRADE, EnhanceAid.failOutcome(10, 15, 35, true, false), "transcended gear: destroy → downgrade");
    }

    @Test
    void charmTurnsLossIntoCharmedButLeavesSafeAlone() {
        assertEquals(Fail.CHARMED, EnhanceAid.failOutcome(10, 15, 35, false, true));
        assertEquals(Fail.CHARMED, EnhanceAid.failOutcome(30, 15, 35, false, true));
        assertEquals(Fail.CHARMED, EnhanceAid.failOutcome(10, 15, 35, true, true));
        assertEquals(Fail.SAFE, EnhanceAid.failOutcome(60, 15, 35, false, true));
    }
}
