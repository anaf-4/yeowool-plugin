package com.yeowool.land.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandTest {

    @Test
    void ownerAlwaysHasEveryPermissionRegardlessOfMemberRoster() {
        UUID owner = UUID.randomUUID();
        Land land = new Land(UUID.randomUUID(), owner);
        assertTrue(land.hasPermission(owner, LandPermission.BUILD));
        assertTrue(land.hasPermission(owner, LandPermission.CONTAINERS));
        assertTrue(land.isMember(owner));
    }

    @Test
    void strangerHasNoPermissionsAndIsNotAMember() {
        Land land = new Land(UUID.randomUUID(), UUID.randomUUID());
        UUID stranger = UUID.randomUUID();
        assertFalse(land.isMember(stranger));
        assertFalse(land.hasPermission(stranger, LandPermission.BUILD));
    }

    @Test
    void memberOnlyHasExplicitlyGrantedPermissions() {
        Land land = new Land(UUID.randomUUID(), UUID.randomUUID());
        UUID member = UUID.randomUUID();
        land.addMember(member, EnumSet.of(LandPermission.BUILD));

        assertTrue(land.isMember(member));
        assertTrue(land.hasPermission(member, LandPermission.BUILD));
        assertFalse(land.hasPermission(member, LandPermission.CONTAINERS));
    }

    @Test
    void memberWithNoPermissionsIsStillAMember() {
        Land land = new Land(UUID.randomUUID(), UUID.randomUUID());
        UUID member = UUID.randomUUID();
        land.addMember(member, Set.of());

        assertTrue(land.isMember(member));
        assertFalse(land.hasPermission(member, LandPermission.BUILD));
        assertFalse(land.hasPermission(member, LandPermission.CONTAINERS));
    }

    @Test
    void setPermissionsReplacesRatherThanMerges() {
        Land land = new Land(UUID.randomUUID(), UUID.randomUUID());
        UUID member = UUID.randomUUID();
        land.addMember(member, EnumSet.allOf(LandPermission.class));

        land.setPermissions(member, EnumSet.of(LandPermission.CONTAINERS));

        assertFalse(land.hasPermission(member, LandPermission.BUILD));
        assertTrue(land.hasPermission(member, LandPermission.CONTAINERS));
    }

    @Test
    void removeMemberRevokesMembershipEntirely() {
        Land land = new Land(UUID.randomUUID(), UUID.randomUUID());
        UUID member = UUID.randomUUID();
        land.addMember(member, EnumSet.allOf(LandPermission.class));

        land.removeMember(member);

        assertFalse(land.isMember(member));
        assertFalse(land.hasPermission(member, LandPermission.BUILD));
    }

    @Test
    void bankBalanceCannotGoNegative() {
        Land land = new Land(UUID.randomUUID(), UUID.randomUUID());
        land.setBankBalance(100);

        assertEquals(150, land.addBankBalance(50));
        assertEquals(-1, land.addBankBalance(-1000), "withdrawal beyond balance is rejected, not clamped to zero");
        assertEquals(150, land.getBankBalance(), "rejected withdrawal must not change the balance");
    }
}
