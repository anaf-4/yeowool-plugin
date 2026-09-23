package com.yeowool.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationRulesTest {

    @Test
    void deputyCapIsLevelDividedByTenFlooredDown() {
        assertEquals(0, FederationRules.deputyCap(1));
        assertEquals(0, FederationRules.deputyCap(9));
        assertEquals(1, FederationRules.deputyCap(10));
        assertEquals(1, FederationRules.deputyCap(19));
        assertEquals(2, FederationRules.deputyCap(25));
        assertEquals(3, FederationRules.deputyCap(30));
    }

    @Test
    void leaderAndDeputyCanApprove() {
        assertTrue(FederationRules.canApprove(FederationRole.LEADER));
        assertTrue(FederationRules.canApprove(FederationRole.DEPUTY));
        assertFalse(FederationRules.canApprove(FederationRole.MEMBER));
    }

    @Test
    void onlyLeaderCanKickADeputy() {
        assertTrue(FederationRules.canKick(FederationRole.LEADER, FederationRole.DEPUTY));
        assertFalse(FederationRules.canKick(FederationRole.DEPUTY, FederationRole.DEPUTY));
    }

    @Test
    void leaderAndDeputyCanKickAPlainMember() {
        assertTrue(FederationRules.canKick(FederationRole.LEADER, FederationRole.MEMBER));
        assertTrue(FederationRules.canKick(FederationRole.DEPUTY, FederationRole.MEMBER));
        assertFalse(FederationRules.canKick(FederationRole.MEMBER, FederationRole.MEMBER));
    }

    @Test
    void noOneCanKickTheLeader() {
        assertFalse(FederationRules.canKick(FederationRole.LEADER, FederationRole.LEADER));
        assertFalse(FederationRules.canKick(FederationRole.DEPUTY, FederationRole.LEADER));
    }
}
