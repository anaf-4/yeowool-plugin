package com.yeowool.federation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
        assertFalse(FederationRules.canKick(FederationRole.MEMBER, FederationRole.LEADER));
    }

    @Test
    void aPlainMemberCannotKickADeputy() {
        assertFalse(FederationRules.canKick(FederationRole.MEMBER, FederationRole.DEPUTY));
    }

    @Test
    void successorPrefersADeputyOverAnEarlierMember() {
        FederationMember earlyMember = member(FederationRole.MEMBER, 100);
        FederationMember lateDeputy = member(FederationRole.DEPUTY, 500);
        assertEquals(lateDeputy, FederationRules.pickSuccessor(List.of(earlyMember, lateDeputy)).orElseThrow());
    }

    @Test
    void successorAmongEqualRolesIsTheEarliestJoiner() {
        FederationMember late = member(FederationRole.MEMBER, 300);
        FederationMember early = member(FederationRole.MEMBER, 100);
        assertEquals(early, FederationRules.pickSuccessor(List.of(late, early)).orElseThrow());
    }

    @Test
    void noSuccessorWhenNoOneElseIsLeft() {
        assertTrue(FederationRules.pickSuccessor(List.of()).isEmpty());
    }

    private static FederationMember member(FederationRole role, long joinedAt) {
        return new FederationMember(UUID.randomUUID(), UUID.randomUUID(), role, joinedAt);
    }
}
