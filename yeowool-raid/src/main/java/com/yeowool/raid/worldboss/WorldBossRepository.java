package com.yeowool.raid.worldboss;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Boss spots and the single shared state row (id = 1) — only the owner server writes the state. */
public final class WorldBossRepository {

    public record Spot(String name, String world, double x, double y, double z) {
    }

    /**
     * {@code phase}: IDLE / ANNOUNCED / ACTIVE. {@code lastOutcome}: KILLED / ESCAPED / FAILED (meaningful
     * while IDLE after an event). {@code lastTop}: up to three top damage dealers' names joined by '|'.
     */
    public record State(long seq, String phase, Spot spot, String eventDate, long spawnAt, long despawnAt,
                        String bossUuid, String lastOutcome, String lastTop) {
    }

    private static final String SPOTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_worldboss_spots (
                name VARCHAR(32) NOT NULL PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String STATE_DDL = """
            CREATE TABLE IF NOT EXISTS yw_worldboss_state (
                id TINYINT NOT NULL PRIMARY KEY,
                seq BIGINT NOT NULL DEFAULT 0,
                phase VARCHAR(16) NOT NULL DEFAULT 'IDLE',
                spot_name VARCHAR(32) NULL,
                world VARCHAR(64) NULL,
                x DOUBLE NULL,
                y DOUBLE NULL,
                z DOUBLE NULL,
                event_date VARCHAR(10) NULL,
                spawn_at BIGINT NOT NULL DEFAULT 0,
                despawn_at BIGINT NOT NULL DEFAULT 0,
                boss_uuid CHAR(36) NULL,
                last_outcome VARCHAR(16) NULL,
                last_top VARCHAR(255) NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public WorldBossRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(SPOTS_DDL);
            statement.executeUpdate(STATE_DDL);
            statement.executeUpdate("INSERT IGNORE INTO yw_worldboss_state (id) VALUES (1)");
        }
    }

    public void saveSpot(Spot spot) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_worldboss_spots (name, world, x, y, z) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z)")) {
            ps.setString(1, spot.name());
            ps.setString(2, spot.world());
            ps.setDouble(3, spot.x());
            ps.setDouble(4, spot.y());
            ps.setDouble(5, spot.z());
            ps.executeUpdate();
        }
    }

    public boolean deleteSpot(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_worldboss_spots WHERE name = ?")) {
            ps.setString(1, name);
            return ps.executeUpdate() == 1;
        }
    }

    public List<Spot> spots() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT name, world, x, y, z FROM yw_worldboss_spots ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Spot> spots = new ArrayList<>();
            while (rs.next()) {
                spots.add(new Spot(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5)));
            }
            return spots;
        }
    }

    public State state() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT seq, phase, spot_name, world, x, y, z, event_date, spawn_at, despawn_at, boss_uuid, last_outcome, last_top "
                             + "FROM yw_worldboss_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("yw_worldboss_state 행이 없습니다");
            }
            String spotName = rs.getString(3);
            Spot spot = spotName == null ? null : new Spot(spotName, rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7));
            return new State(rs.getLong(1), rs.getString(2), spot, rs.getString(8), rs.getLong(9), rs.getLong(10),
                    rs.getString(11), rs.getString(12), rs.getString(13));
        }
    }

    public void saveState(State state) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_worldboss_state SET seq = ?, phase = ?, spot_name = ?, world = ?, x = ?, y = ?, z = ?, event_date = ?, "
                             + "spawn_at = ?, despawn_at = ?, boss_uuid = ?, last_outcome = ?, last_top = ? WHERE id = 1")) {
            Spot spot = state.spot();
            ps.setLong(1, state.seq());
            ps.setString(2, state.phase());
            ps.setString(3, spot == null ? null : spot.name());
            ps.setString(4, spot == null ? null : spot.world());
            ps.setObject(5, spot == null ? null : spot.x());
            ps.setObject(6, spot == null ? null : spot.y());
            ps.setObject(7, spot == null ? null : spot.z());
            ps.setString(8, state.eventDate());
            ps.setLong(9, state.spawnAt());
            ps.setLong(10, state.despawnAt());
            ps.setString(11, state.bossUuid());
            ps.setString(12, state.lastOutcome());
            ps.setString(13, state.lastTop());
            ps.executeUpdate();
        }
    }
}
