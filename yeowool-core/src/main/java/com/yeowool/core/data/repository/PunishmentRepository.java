package com.yeowool.core.data.repository;

import com.yeowool.core.api.model.PunishmentEntry;
import com.yeowool.core.api.model.PunishmentType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_punishments}. Every method here does
 * real I/O and must only be called off the main server thread.
 */
public final class PunishmentRepository {

    private final DataSource dataSource;

    public PunishmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(UUID target, PunishmentType type, String reason, UUID staff, long createdAt, Long expiresAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_punishments (target, type, reason, staff, created_at, expires_at, active) VALUES (?, ?, ?, ?, ?, ?, 1)")) {
            insert.setString(1, target.toString());
            insert.setString(2, type.name());
            insert.setString(3, reason);
            insert.setString(4, staff == null ? null : staff.toString());
            insert.setLong(5, createdAt);
            if (expiresAt == null) {
                insert.setNull(6, Types.BIGINT);
            } else {
                insert.setLong(6, expiresAt);
            }
            insert.executeUpdate();
        }
    }

    /** Most recent still-in-effect (active=1 and not expired) entry of the given type, if any. */
    public Optional<PunishmentEntry> findActive(UUID target, PunishmentType type) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, reason, staff, created_at, expires_at, points FROM yw_punishments "
                             + "WHERE target = ? AND type = ? AND active = 1 AND (expires_at IS NULL OR expires_at > ?) "
                             + "ORDER BY created_at DESC LIMIT 1")) {
            select.setString(1, target.toString());
            select.setString(2, type.name());
            select.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String staffStr = rs.getString("staff");
                long expiresAtRaw = rs.getLong("expires_at");
                Long expiresAt = rs.wasNull() ? null : expiresAtRaw;
                return Optional.of(new PunishmentEntry(rs.getLong("id"), target, type, rs.getString("reason"),
                        staffStr == null ? null : UUID.fromString(staffStr), rs.getLong("created_at"), expiresAt,
                        true, null, null, rs.getInt("points")));
            }
        }
    }

    public List<PunishmentEntry> history(UUID target, int limit) throws SQLException {
        List<PunishmentEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, type, reason, staff, created_at, expires_at, active, revoked_by, revoked_at, points FROM yw_punishments "
                             + "WHERE target = ? ORDER BY created_at DESC LIMIT ?")) {
            select.setString(1, target.toString());
            select.setInt(2, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String staffStr = rs.getString("staff");
                    String revokedByStr = rs.getString("revoked_by");
                    long expiresAtRaw = rs.getLong("expires_at");
                    Long expiresAt = rs.wasNull() ? null : expiresAtRaw;
                    long revokedAtRaw = rs.getLong("revoked_at");
                    Long revokedAt = rs.wasNull() ? null : revokedAtRaw;
                    entries.add(new PunishmentEntry(rs.getLong("id"), target, PunishmentType.valueOf(rs.getString("type")),
                            rs.getString("reason"), staffStr == null ? null : UUID.fromString(staffStr),
                            rs.getLong("created_at"), expiresAt, rs.getBoolean("active"),
                            revokedByStr == null ? null : UUID.fromString(revokedByStr), revokedAt, rs.getInt("points")));
                }
            }
        }
        return entries;
    }

    /**
     * Inserts one 경고 지급/회수 event as its own permanent row (type WARN) —
     * {@code points} is signed: positive for a grant, negative for a revoke —
     * rather than mutating a single running counter, so every change to a
     * player's warning count stays in the ledger. {@code expiresAt} (null =
     * never expires) lets a single grant's points stop counting toward the
     * total after a staff-chosen date, without needing a separate revoke.
     */
    public void insertWarning(UUID target, String reason, UUID staff, long createdAt, int points, Long expiresAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_punishments (target, type, reason, staff, created_at, points, expires_at, active) VALUES (?, 'WARN', ?, ?, ?, ?, ?, 1)")) {
            insert.setString(1, target.toString());
            insert.setString(2, reason);
            insert.setString(3, staff == null ? null : staff.toString());
            insert.setLong(4, createdAt);
            insert.setInt(5, points);
            if (expiresAt == null) {
                insert.setNull(6, Types.BIGINT);
            } else {
                insert.setLong(6, expiresAt);
            }
            insert.executeUpdate();
        }
    }

    /**
     * Sum of every not-yet-expired WARN row's points for this target (grants
     * minus revokes) — the player's current cumulative warning count. A
     * grant whose {@code expires_at} has passed stops contributing, though
     * the row itself stays in {@link #history} for the audit trail.
     */
    public int sumWarningPoints(UUID target) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT COALESCE(SUM(points), 0) AS total FROM yw_punishments "
                             + "WHERE target = ? AND type = 'WARN' AND (expires_at IS NULL OR expires_at > ?)")) {
            select.setString(1, target.toString());
            select.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    /**
     * Same as {@link #sumWarningPoints(UUID)} but for several targets in one
     * round trip — meant for a periodic sweep over every active auto-ban
     * (see {@link #findActiveByReasonPrefix}) instead of querying each
     * target one at a time. A target with no WARN rows at all is simply
     * absent from the result map (equivalent to a total of 0).
     */
    public Map<UUID, Integer> sumWarningPointsForTargets(Collection<UUID> targets) throws SQLException {
        Map<UUID, Integer> totals = new HashMap<>();
        if (targets.isEmpty()) {
            return totals;
        }
        String placeholders = String.join(",", Collections.nCopies(targets.size(), "?"));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT target, COALESCE(SUM(points), 0) AS total FROM yw_punishments "
                             + "WHERE type = 'WARN' AND (expires_at IS NULL OR expires_at > ?) AND target IN (" + placeholders + ") "
                             + "GROUP BY target")) {
            select.setLong(1, System.currentTimeMillis());
            int index = 2;
            for (UUID target : targets) {
                select.setString(index++, target.toString());
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    totals.put(UUID.fromString(rs.getString("target")), rs.getInt("total"));
                }
            }
        }
        return totals;
    }

    /**
     * Every currently active (not revoked, not expired) entry of the given
     * type whose reason starts with {@code reasonPrefix} — across every
     * target, not just one. Meant for a periodic self-healing sweep (e.g.
     * lifting a warning-triggered auto-ban whose points have since expired
     * on their own, with nobody having run a manual 회수 to trigger the
     * usual check), so it has to scan globally rather than per-player.
     */
    public List<PunishmentEntry> findActiveByReasonPrefix(PunishmentType type, String reasonPrefix) throws SQLException {
        List<PunishmentEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, target, reason, staff, created_at, expires_at, points FROM yw_punishments "
                             + "WHERE type = ? AND active = 1 AND (expires_at IS NULL OR expires_at > ?) AND reason LIKE ?")) {
            select.setString(1, type.name());
            select.setLong(2, System.currentTimeMillis());
            select.setString(3, reasonPrefix.replace("%", "\\%").replace("_", "\\_") + "%");
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String staffStr = rs.getString("staff");
                    long expiresAtRaw = rs.getLong("expires_at");
                    Long expiresAt = rs.wasNull() ? null : expiresAtRaw;
                    entries.add(new PunishmentEntry(rs.getLong("id"), UUID.fromString(rs.getString("target")), type,
                            rs.getString("reason"), staffStr == null ? null : UUID.fromString(staffStr),
                            rs.getLong("created_at"), expiresAt, true, null, null, rs.getInt("points")));
                }
            }
        }
        return entries;
    }

    /** Deactivates every currently-active entry of the given type for this target. Returns the number of rows affected. */
    public int revokeActive(UUID target, PunishmentType type, UUID revokedBy) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_punishments SET active = 0, revoked_by = ?, revoked_at = ? WHERE target = ? AND type = ? AND active = 1")) {
            update.setString(1, revokedBy == null ? null : revokedBy.toString());
            update.setLong(2, System.currentTimeMillis());
            update.setString(3, target.toString());
            update.setString(4, type.name());
            return update.executeUpdate();
        }
    }
}
