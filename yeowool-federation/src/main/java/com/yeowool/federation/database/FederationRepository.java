package com.yeowool.federation.database;

import com.yeowool.federation.Federation;
import com.yeowool.federation.FederationMember;
import com.yeowool.federation.FederationRole;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Blocking JDBC access to `yw_federations`/`yw_federation_members`/`yw_federation_applications`. Must only be called off the main thread. */
public final class FederationRepository {

    private final DataSource dataSource;

    public FederationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs {@code work} on a single {@link Connection} with auto-commit disabled, committing on
     * success and rolling back on any {@link SQLException} — for the handful of call sites where
     * two or more writes must land together (see {@code FederationManager.create()} and
     * {@code transferLeadership()}). Everything else in this repository still uses one
     * connection-per-statement, which is fine for writes that don't need atomicity with each other.
     */
    public <T> T withTransaction(SqlTransaction<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @FunctionalInterface
    public interface SqlTransaction<T> {
        T run(Connection connection) throws SQLException;
    }

    public void insert(Federation federation) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, federation);
        }
    }

    public void insert(Connection connection, Federation federation) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO yw_federations (id, name, description, level, leader_land_id, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, federation.id().toString());
            insert.setString(2, federation.name());
            insert.setString(3, federation.description());
            insert.setInt(4, federation.level());
            insert.setString(5, federation.leaderLandId().toString());
            insert.setLong(6, federation.createdAt());
            insert.executeUpdate();
        }
    }

    public void update(Federation federation) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            update(connection, federation);
        }
    }

    public void update(Connection connection, Federation federation) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE yw_federations SET description = ?, leader_land_id = ? WHERE id = ?")) {
            update.setString(1, federation.description());
            update.setString(2, federation.leaderLandId().toString());
            update.setString(3, federation.id().toString());
            update.executeUpdate();
        }
    }

    public void delete(UUID federationId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement deleteMembers = connection.prepareStatement(
                    "DELETE FROM yw_federation_members WHERE federation_id = ?")) {
                deleteMembers.setString(1, federationId.toString());
                deleteMembers.executeUpdate();
            }
            try (PreparedStatement deleteApplications = connection.prepareStatement(
                    "DELETE FROM yw_federation_applications WHERE federation_id = ?")) {
                deleteApplications.setString(1, federationId.toString());
                deleteApplications.executeUpdate();
            }
            try (PreparedStatement deleteFederation = connection.prepareStatement(
                    "DELETE FROM yw_federations WHERE id = ?")) {
                deleteFederation.setString(1, federationId.toString());
                deleteFederation.executeUpdate();
            }
        }
    }

    public Optional<Federation> findById(UUID federationId) throws SQLException {
        return querySingleFederation("SELECT id, name, description, level, leader_land_id, created_at FROM yw_federations WHERE id = ?", federationId.toString());
    }

    public Optional<Federation> findByName(String name) throws SQLException {
        return querySingleFederation("SELECT id, name, description, level, leader_land_id, created_at FROM yw_federations WHERE name = ?", name);
    }

    public List<Federation> loadAll() throws SQLException {
        List<Federation> federations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, name, description, level, leader_land_id, created_at FROM yw_federations");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                federations.add(readFederation(rs));
            }
        }
        return federations;
    }

    private Optional<Federation> querySingleFederation(String sql, String param) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, param);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readFederation(rs));
            }
        }
    }

    private Federation readFederation(ResultSet rs) throws SQLException {
        return new Federation(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("level"),
                UUID.fromString(rs.getString("leader_land_id")),
                rs.getLong("created_at")
        );
    }

    public void insertMember(FederationMember member) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insertMember(connection, member);
        }
    }

    public void insertMember(Connection connection, FederationMember member) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO yw_federation_members (federation_id, land_id, role, joined_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, member.federationId().toString());
            insert.setString(2, member.landId().toString());
            insert.setString(3, member.role().name());
            insert.setLong(4, member.joinedAt());
            insert.executeUpdate();
        }
    }

    public void updateMemberRole(UUID landId, FederationRole newRole) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            updateMemberRole(connection, landId, newRole);
        }
    }

    public void updateMemberRole(Connection connection, UUID landId, FederationRole newRole) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE yw_federation_members SET role = ? WHERE land_id = ?")) {
            update.setString(1, newRole.name());
            update.setString(2, landId.toString());
            update.executeUpdate();
        }
    }

    public void deleteMember(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_federation_members WHERE land_id = ?")) {
            delete.setString(1, landId.toString());
            delete.executeUpdate();
        }
    }

    public Optional<FederationMember> findMemberByLandId(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT federation_id, land_id, role, joined_at FROM yw_federation_members WHERE land_id = ?")) {
            select.setString(1, landId.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readMember(rs));
            }
        }
    }

    public List<FederationMember> loadMembers(UUID federationId) throws SQLException {
        List<FederationMember> members = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT federation_id, land_id, role, joined_at FROM yw_federation_members WHERE federation_id = ?")) {
            select.setString(1, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    members.add(readMember(rs));
                }
            }
        }
        return members;
    }

    private FederationMember readMember(ResultSet rs) throws SQLException {
        return new FederationMember(
                UUID.fromString(rs.getString("federation_id")),
                UUID.fromString(rs.getString("land_id")),
                FederationRole.valueOf(rs.getString("role")),
                rs.getLong("joined_at")
        );
    }

    public void insertApplication(UUID federationId, UUID landId, long appliedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_federation_applications (federation_id, land_id, applied_at) VALUES (?, ?, ?)")) {
            insert.setString(1, federationId.toString());
            insert.setString(2, landId.toString());
            insert.setLong(3, appliedAt);
            insert.executeUpdate();
        }
    }

    public void deleteApplication(UUID federationId, UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_federation_applications WHERE federation_id = ? AND land_id = ?")) {
            delete.setString(1, federationId.toString());
            delete.setString(2, landId.toString());
            delete.executeUpdate();
        }
    }

    public void deleteAllApplicationsForLand(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_federation_applications WHERE land_id = ?")) {
            delete.setString(1, landId.toString());
            delete.executeUpdate();
        }
    }

    public boolean applicationExists(UUID federationId, UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT 1 FROM yw_federation_applications WHERE federation_id = ? AND land_id = ?")) {
            select.setString(1, federationId.toString());
            select.setString(2, landId.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<UUID> loadApplicantLandIds(UUID federationId) throws SQLException {
        List<UUID> landIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT land_id FROM yw_federation_applications WHERE federation_id = ?")) {
            select.setString(1, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    landIds.add(UUID.fromString(rs.getString("land_id")));
                }
            }
        }
        return landIds;
    }
}
