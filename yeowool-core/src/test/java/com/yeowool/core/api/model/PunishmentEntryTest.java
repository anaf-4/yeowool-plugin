package com.yeowool.core.api.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentEntryTest {

    private static PunishmentEntry entry(Long expiresAt, boolean active) {
        return new PunishmentEntry(1L, UUID.randomUUID(), PunishmentType.BAN, "test", null,
                System.currentTimeMillis(), expiresAt, active, null, null, 0);
    }

    @Test
    void nullExpiryIsPermanent() {
        assertTrue(entry(null, true).isPermanent());
        assertFalse(entry(System.currentTimeMillis() + 60_000, true).isPermanent());
    }

    @Test
    void expiryInThePastIsExpired() {
        assertTrue(entry(System.currentTimeMillis() - 1000, true).isExpired());
    }

    @Test
    void expiryInTheFutureIsNotExpired() {
        assertFalse(entry(System.currentTimeMillis() + 60_000, true).isExpired());
    }

    @Test
    void permanentEntryIsNeverExpired() {
        assertFalse(entry(null, true).isExpired());
    }

    @Test
    void currentlyActiveRequiresBothActiveFlagAndNotExpired() {
        assertTrue(entry(null, true).isCurrentlyActive());
        assertFalse(entry(null, false).isCurrentlyActive(), "revoked entries aren't currently active even if permanent");
        assertFalse(entry(System.currentTimeMillis() - 1000, true).isCurrentlyActive(), "expired entries aren't currently active even if not revoked");
    }
}
