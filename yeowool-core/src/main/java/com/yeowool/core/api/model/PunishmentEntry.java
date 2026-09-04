package com.yeowool.core.api.model;

import java.util.UUID;

/**
 * One row of a player's moderation history, persisted to {@code yw_punishments}
 * through {@link com.yeowool.core.api.service.PunishmentService}. {@code
 * expiresAt} of {@code null} means permanent; {@code active=false} means a
 * staff member explicitly revoked it (unban/unmute) before it would have
 * expired on its own. {@code points} is only meaningful for {@link
 * PunishmentType#WARN} rows: each grant/revoke is its own row carrying a
 * signed delta (positive for a grant, negative for a revoke), so a player's
 * cumulative warning count is the sum of every WARN row's points rather than
 * a single mutable counter — every change stays in the permanent record.
 */
public record PunishmentEntry(
        long id,
        UUID target,
        PunishmentType type,
        String reason,
        UUID staff,
        long createdAt,
        Long expiresAt,
        boolean active,
        UUID revokedBy,
        Long revokedAt,
        int points
) {
    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() >= expiresAt;
    }

    /** Still in effect right now: not revoked, and (permanent or not yet expired). */
    public boolean isCurrentlyActive() {
        return active && !isExpired();
    }
}
