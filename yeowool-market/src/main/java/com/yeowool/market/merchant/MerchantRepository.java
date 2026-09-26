package com.yeowool.market.merchant;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Candidate spots plus one shared state row (id = 1) for the wandering
 * merchant. Spawn/despawn transitions are UPDATEs conditional on the
 * {@code seq} the caller read, and each bumps it — so when all three servers
 * notice the same due transition, exactly one of them makes it.
 */
public final class MerchantRepository {

    public record Spot(String name, String serverId, String world, double x, double y, double z, float yaw, float pitch) {
    }

    /** {@code spot} is null while no merchant is out. */
    public record State(long seq, boolean active, Spot spot, long despawnAt, long nextSpawnAt) {
    }

    private static final String SPOTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_merchant_spots (
                name VARCHAR(32) NOT NULL PRIMARY KEY,
                server_id VARCHAR(32) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String STATE_DDL = """
            CREATE TABLE IF NOT EXISTS yw_merchant_state (
                id TINYINT NOT NULL PRIMARY KEY,
                seq BIGINT NOT NULL DEFAULT 0,
                active TINYINT(1) NOT NULL DEFAULT 0,
                spot_name VARCHAR(32) NULL,
                server_id VARCHAR(32) NULL,
                world VARCHAR(64) NULL,
                x DOUBLE NULL,
                y DOUBLE NULL,
                z DOUBLE NULL,
                yaw FLOAT NULL,
                pitch FLOAT NULL,
                despawn_at BIGINT NOT NULL DEFAULT 0,
                next_spawn_at BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public MerchantRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates the tables and, only the very first time, the state row with the first appearance at {@code firstSpawnAt}. */
    public void createTables(long firstSpawnAt) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(SPOTS_DDL);
                statement.executeUpdate(STATE_DDL);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_merchant_state (id, next_spawn_at) VALUES (1, ?)")) {
                ps.setLong(1, firstSpawnAt);
                ps.executeUpdate();
            }
        }
    }

    public void saveSpot(Spot spot) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_merchant_spots (name, server_id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE server_id = VALUES(server_id), world = VALUES(world), x = VALUES(x), "
                             + "y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
            ps.setString(1, spot.name());
            ps.setString(2, spot.serverId());
            ps.setString(3, spot.world());
            ps.setDouble(4, spot.x());
            ps.setDouble(5, spot.y());
            ps.setDouble(6, spot.z());
            ps.setFloat(7, spot.yaw());
            ps.setFloat(8, spot.pitch());
            ps.executeUpdate();
        }
    }

    public boolean deleteSpot(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_merchant_spots WHERE name = ?")) {
            ps.setString(1, name);
            return ps.executeUpdate() == 1;
        }
    }

    public List<Spot> spots() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT name, server_id, world, x, y, z, yaw, pitch FROM yw_merchant_spots ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Spot> spots = new ArrayList<>();
            while (rs.next()) {
                spots.add(new Spot(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getFloat(7), rs.getFloat(8)));
            }
            return spots;
        }
    }

    public State state() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT seq, active, spot_name, server_id, world, x, y, z, yaw, pitch, despawn_at, next_spawn_at "
                             + "FROM yw_merchant_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("yw_merchant_state 행이 없습니다");
            }
            boolean active = rs.getBoolean(2);
            Spot spot = active
                    ? new Spot(rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getDouble(6), rs.getDouble(7), rs.getDouble(8), rs.getFloat(9), rs.getFloat(10))
                    : null;
            return new State(rs.getLong(1), active, spot, rs.getLong(11), rs.getLong(12));
        }
    }

    public boolean spawn(long expectedSeq, Spot spot, long despawnAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_merchant_state SET seq = seq + 1, active = 1, spot_name = ?, server_id = ?, world = ?, "
                             + "x = ?, y = ?, z = ?, yaw = ?, pitch = ?, despawn_at = ? WHERE id = 1 AND seq = ? AND active = 0")) {
            ps.setString(1, spot.name());
            ps.setString(2, spot.serverId());
            ps.setString(3, spot.world());
            ps.setDouble(4, spot.x());
            ps.setDouble(5, spot.y());
            ps.setDouble(6, spot.z());
            ps.setFloat(7, spot.yaw());
            ps.setFloat(8, spot.pitch());
            ps.setLong(9, despawnAt);
            ps.setLong(10, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean despawn(long expectedSeq, long nextSpawnAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_merchant_state SET seq = seq + 1, active = 0, next_spawn_at = ? WHERE id = 1 AND seq = ? AND active = 1")) {
            ps.setLong(1, nextSpawnAt);
            ps.setLong(2, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }

    /** Makes the next tick spawn a merchant; false if one is already out. */
    public boolean requestSpawnNow() throws SQLException {
        return executeOnState("UPDATE yw_merchant_state SET next_spawn_at = 0 WHERE id = 1 AND active = 0");
    }

    /** Makes the next tick send the merchant away; false if none is out. */
    public boolean requestDespawnNow() throws SQLException {
        return executeOnState("UPDATE yw_merchant_state SET despawn_at = 0 WHERE id = 1 AND active = 1");
    }

    private boolean executeOnState(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            return ps.executeUpdate() == 1;
        }
    }
}
