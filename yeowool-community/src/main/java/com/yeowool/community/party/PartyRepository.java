package com.yeowool.community.party;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Raw DB access for the party system — see {@link PartyManager} for the actual rules/caching on top of this. */
public final class PartyRepository {

    public record PartyRow(long id, String name, UUID leader, int maxSize, PartyManager.JoinMode joinMode) {
    }

    public record MemberRow(UUID uuid, String name, long joinedAt) {
    }

    public record PresenceRow(String server, String name, double health, double maxHealth, long updatedAt) {
    }

    public record JoinRequestRow(UUID uuid, long partyId, String name, long requestedAt) {
    }

    private final DataSource dataSource;

    public PartyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates the party and adds {@code leader} as its first member, in one transaction. */
    public long createParty(String name, UUID leader, String leaderName, int maxSize, PartyManager.JoinMode joinMode) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long partyId;
                try (PreparedStatement insertParty = connection.prepareStatement(
                        "INSERT INTO yw_party (name, leader_uuid, max_size, created_at, join_mode) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insertParty.setString(1, name);
                    insertParty.setString(2, leader.toString());
                    insertParty.setInt(3, maxSize);
                    insertParty.setLong(4, now);
                    insertParty.setString(5, joinMode.name());
                    insertParty.executeUpdate();
                    try (ResultSet keys = insertParty.getGeneratedKeys()) {
                        keys.next();
                        partyId = keys.getLong(1);
                    }
                }
                try (PreparedStatement insertMember = connection.prepareStatement(
                        "INSERT INTO yw_party_member (uuid, party_id, name, joined_at) VALUES (?, ?, ?, ?)")) {
                    insertMember.setString(1, leader.toString());
                    insertMember.setLong(2, partyId);
                    insertMember.setString(3, leaderName);
                    insertMember.setLong(4, now);
                    insertMember.executeUpdate();
                }
                connection.commit();
                return partyId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<PartyRow> findByName(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, name, leader_uuid, max_size, join_mode FROM yw_party WHERE name = ?")) {
            select.setString(1, name);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(toPartyRow(rs)) : Optional.empty();
            }
        }
    }

    public Optional<PartyRow> findById(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, name, leader_uuid, max_size, join_mode FROM yw_party WHERE id = ?")) {
            select.setLong(1, id);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(toPartyRow(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Long> findPartyIdOf(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT party_id FROM yw_party_member WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    /** Batch lookup — one row per uuid that's currently in a party. Used by the per-tick HUD refresh. */
    public Map<UUID, Long> findPartyIdsOf(Collection<UUID> uuids) throws SQLException {
        Map<UUID, Long> result = new HashMap<>();
        if (uuids.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", uuids.stream().map(u -> "?").toList());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid, party_id FROM yw_party_member WHERE uuid IN (" + placeholders + ")")) {
            int i = 1;
            for (UUID uuid : uuids) {
                select.setString(i++, uuid.toString());
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getLong("party_id"));
                }
            }
        }
        return result;
    }

    public List<MemberRow> findMembers(long partyId) throws SQLException {
        List<MemberRow> members = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid, name, joined_at FROM yw_party_member WHERE party_id = ? ORDER BY joined_at ASC")) {
            select.setLong(1, partyId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    members.add(new MemberRow(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("joined_at")));
                }
            }
        }
        return members;
    }

    /** Batch — every party row and every member row for the given party ids, in one round trip each. */
    public Map<Long, PartyRow> findParties(Collection<Long> partyIds) throws SQLException {
        Map<Long, PartyRow> result = new HashMap<>();
        if (partyIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", partyIds.stream().map(id -> "?").toList());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, name, leader_uuid, max_size, join_mode FROM yw_party WHERE id IN (" + placeholders + ")")) {
            int i = 1;
            for (Long id : partyIds) {
                select.setLong(i++, id);
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    PartyRow row = toPartyRow(rs);
                    result.put(row.id(), row);
                }
            }
        }
        return result;
    }

    public Map<Long, List<MemberRow>> findMembersOf(Collection<Long> partyIds) throws SQLException {
        Map<Long, List<MemberRow>> result = new LinkedHashMap<>();
        if (partyIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", partyIds.stream().map(id -> "?").toList());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid, party_id, name, joined_at FROM yw_party_member WHERE party_id IN (" + placeholders + ") ORDER BY joined_at ASC")) {
            int i = 1;
            for (Long id : partyIds) {
                select.setLong(i++, id);
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getLong("party_id"), key -> new ArrayList<>())
                            .add(new MemberRow(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("joined_at")));
                }
            }
        }
        return result;
    }

    public void addMember(long partyId, UUID uuid, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_party_member (uuid, party_id, name, joined_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setLong(2, partyId);
            insert.setString(3, name);
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    public void removeMember(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_party_member WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
    }

    public int countMembers(long partyId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_party_member WHERE party_id = ?")) {
            select.setLong(1, partyId);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void updateLeader(long partyId, UUID newLeader) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_party SET leader_uuid = ? WHERE id = ?")) {
            update.setString(1, newLeader.toString());
            update.setLong(2, partyId);
            update.executeUpdate();
        }
    }

    /** Deletes the party row and every membership row for it, in one transaction. */
    public void deleteParty(long partyId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteMembers = connection.prepareStatement(
                        "DELETE FROM yw_party_member WHERE party_id = ?")) {
                    deleteMembers.setLong(1, partyId);
                    deleteMembers.executeUpdate();
                }
                try (PreparedStatement deleteRequests = connection.prepareStatement(
                        "DELETE FROM yw_party_join_request WHERE party_id = ?")) {
                    deleteRequests.setLong(1, partyId);
                    deleteRequests.executeUpdate();
                }
                try (PreparedStatement deleteParty = connection.prepareStatement(
                        "DELETE FROM yw_party WHERE id = ?")) {
                    deleteParty.setLong(1, partyId);
                    deleteParty.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Upserts one online party member's live health snapshot — read back by other servers' HUD refresh. */
    public void upsertPresence(UUID uuid, String server, String name, double health, double maxHealth) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_party_presence (uuid, server, name, health, max_health, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE server = VALUES(server), name = VALUES(name), health = VALUES(health), "
                             + "max_health = VALUES(max_health), updated_at = VALUES(updated_at)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, server);
            upsert.setString(3, name);
            upsert.setDouble(4, health);
            upsert.setDouble(5, maxHealth);
            upsert.setLong(6, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }

    public Map<UUID, PresenceRow> findPresence(Collection<UUID> uuids) throws SQLException {
        Map<UUID, PresenceRow> result = new HashMap<>();
        if (uuids.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", uuids.stream().map(u -> "?").toList());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid, server, name, health, max_health, updated_at FROM yw_party_presence WHERE uuid IN (" + placeholders + ")")) {
            int i = 1;
            for (UUID uuid : uuids) {
                select.setString(i++, uuid.toString());
            }
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    result.put(UUID.fromString(rs.getString("uuid")), new PresenceRow(
                            rs.getString("server"), rs.getString("name"), rs.getDouble("health"),
                            rs.getDouble("max_health"), rs.getLong("updated_at")));
                }
            }
        }
        return result;
    }

    public void removePresence(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_party_presence WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
    }

    /** Upserts so re-requesting (e.g. after a deny) just refreshes the timestamp instead of erroring on the duplicate uuid key. */
    public void addJoinRequest(long partyId, UUID uuid, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_party_join_request (uuid, party_id, name, requested_at) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE party_id = VALUES(party_id), name = VALUES(name), requested_at = VALUES(requested_at)")) {
            upsert.setString(1, uuid.toString());
            upsert.setLong(2, partyId);
            upsert.setString(3, name);
            upsert.setLong(4, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }

    public void removeJoinRequest(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_party_join_request WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
    }

    public Optional<JoinRequestRow> findJoinRequestByName(long partyId, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid, party_id, name, requested_at FROM yw_party_join_request WHERE party_id = ? AND name = ?")) {
            select.setLong(1, partyId);
            select.setString(2, name);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(toJoinRequestRow(rs)) : Optional.empty();
            }
        }
    }

    public List<JoinRequestRow> findJoinRequestsOf(long partyId) throws SQLException {
        List<JoinRequestRow> requests = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid, party_id, name, requested_at FROM yw_party_join_request WHERE party_id = ? ORDER BY requested_at ASC")) {
            select.setLong(1, partyId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    requests.add(toJoinRequestRow(rs));
                }
            }
        }
        return requests;
    }

    private JoinRequestRow toJoinRequestRow(ResultSet rs) throws SQLException {
        return new JoinRequestRow(UUID.fromString(rs.getString("uuid")), rs.getLong("party_id"), rs.getString("name"), rs.getLong("requested_at"));
    }

    private PartyRow toPartyRow(ResultSet rs) throws SQLException {
        return new PartyRow(rs.getLong("id"), rs.getString("name"), UUID.fromString(rs.getString("leader_uuid")), rs.getInt("max_size"),
                PartyManager.JoinMode.valueOf(rs.getString("join_mode")));
    }
}
