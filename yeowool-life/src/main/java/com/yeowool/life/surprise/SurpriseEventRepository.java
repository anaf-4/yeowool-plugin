package com.yeowool.life.surprise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The single shared surprise-event row (id = 1). Start/end are UPDATEs conditional on the
 * {@code seq} the caller read and bump it, so when both participating servers notice the same
 * due transition exactly one of them makes it.
 */
public final class SurpriseEventRepository {

    /** {@code type} stays set after an event ends (it's what ended); {@code lastType} is the one to avoid next. */
    public record State(long seq, boolean active, SurpriseEventType type, double multiplier, long endsAt, long nextAt,
                        SurpriseEventType lastType) {
    }

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_surprise_event_state (
                id TINYINT NOT NULL PRIMARY KEY,
                seq BIGINT NOT NULL DEFAULT 0,
                active TINYINT(1) NOT NULL DEFAULT 0,
                type VARCHAR(16) NULL,
                multiplier DOUBLE NOT NULL DEFAULT 1,
                ends_at BIGINT NOT NULL DEFAULT 0,
                next_at BIGINT NOT NULL DEFAULT 0,
                last_type VARCHAR(16) NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public SurpriseEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates the table and, only the very first time, the row with the first event at {@code firstAt}. */
    public void createTables(long firstAt) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(DDL);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_surprise_event_state (id, next_at) VALUES (1, ?)")) {
                ps.setLong(1, firstAt);
                ps.executeUpdate();
            }
        }
    }

    public State state() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT seq, active, type, multiplier, ends_at, next_at, last_type FROM yw_surprise_event_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("yw_surprise_event_state 행이 없습니다");
            }
            return new State(rs.getLong(1), rs.getBoolean(2), SurpriseEventType.byKey(rs.getString(3)).orElse(null),
                    rs.getDouble(4), rs.getLong(5), rs.getLong(6), SurpriseEventType.byKey(rs.getString(7)).orElse(null));
        }
    }

    public boolean start(long expectedSeq, SurpriseEventType type, double multiplier, long endsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_surprise_event_state SET seq = seq + 1, active = 1, type = ?, multiplier = ?, ends_at = ? "
                             + "WHERE id = 1 AND seq = ? AND active = 0")) {
            ps.setString(1, type.key());
            ps.setDouble(2, multiplier);
            ps.setLong(3, endsAt);
            ps.setLong(4, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean end(long expectedSeq, long nextAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_surprise_event_state SET seq = seq + 1, active = 0, last_type = type, next_at = ? "
                             + "WHERE id = 1 AND seq = ? AND active = 1")) {
            ps.setLong(1, nextAt);
            ps.setLong(2, expectedSeq);
            return ps.executeUpdate() == 1;
        }
    }
}
