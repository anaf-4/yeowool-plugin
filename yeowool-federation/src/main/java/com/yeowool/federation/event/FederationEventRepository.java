package com.yeowool.federation.event;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 연합대항 persistence. Shared by all 3 servers: start is one transaction (event row + activity snapshot),
 * end is claimed by a single conditional UPDATE so only one server pays rewards. Blocking — executor only.
 */
public final class FederationEventRepository {

    private static final int MYSQL_DUPLICATE_KEY = 1062;
    private static final String EVENT_COLUMNS = "id, starts_at, ends_at, ended, ended_at, result";

    private final DataSource dataSource;

    public FederationEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return the new event id, or empty if {@code weekKey} was already used (another server started this week's event). */
    public OptionalLong startEvent(long startsAt, long endsAt, String weekKey) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long eventId;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO yw_federation_events (starts_at, ends_at, week_key) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, startsAt);
                    insert.setLong(2, endsAt);
                    insert.setString(3, weekKey);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        eventId = keys.getLong(1);
                    }
                }
                try (PreparedStatement snapshot = connection.prepareStatement(
                        "INSERT INTO yw_federation_event_baselines (event_id, federation_id, activity) " +
                                "SELECT ?, id, activity FROM yw_federations")) {
                    snapshot.setLong(1, eventId);
                    snapshot.executeUpdate();
                }
                connection.commit();
                return OptionalLong.of(eventId);
            } catch (SQLException e) {
                connection.rollback();
                if (e.getErrorCode() == MYSQL_DUPLICATE_KEY) {
                    return OptionalLong.empty();
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<FederationEvent> findActive() throws SQLException {
        return querySingle("SELECT " + EVENT_COLUMNS + " FROM yw_federation_events WHERE ended = 0 ORDER BY id DESC LIMIT 1");
    }

    public Optional<FederationEvent> findLatest() throws SQLException {
        return querySingle("SELECT " + EVENT_COLUMNS + " FROM yw_federation_events ORDER BY id DESC LIMIT 1");
    }

    public Optional<FederationEvent> findLatestFinished() throws SQLException {
        return querySingle("SELECT " + EVENT_COLUMNS + " FROM yw_federation_events WHERE result IS NOT NULL ORDER BY id DESC LIMIT 1");
    }

    /** @return true for exactly one caller across all servers — that caller pays rewards and saves the result. */
    public boolean claimEnd(long eventId, long endedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_federation_events SET ended = 1, ended_at = ? WHERE id = ? AND ended = 0")) {
            update.setLong(1, endedAt);
            update.setLong(2, eventId);
            return update.executeUpdate() == 1;
        }
    }

    public void saveResult(long eventId, String result) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_federation_events SET result = ? WHERE id = ?")) {
            update.setString(1, result);
            update.setLong(2, eventId);
            update.executeUpdate();
        }
    }

    /** Federations ordered by activity gained since the snapshot, only those that gained at least {@code minGained} (clamped to 1). */
    public List<EventStanding> standings(long eventId, int limit, long minGained) throws SQLException {
        List<EventStanding> standings = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT f.id, f.name, f.activity - COALESCE(b.activity, 0) AS gained " +
                             "FROM yw_federations f " +
                             "LEFT JOIN yw_federation_event_baselines b ON b.federation_id = f.id AND b.event_id = ? " +
                             "WHERE f.activity - COALESCE(b.activity, 0) >= ? " +
                             "ORDER BY gained DESC LIMIT ?")) {
            select.setLong(1, eventId);
            select.setLong(2, Math.max(1, minGained));
            select.setInt(3, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    standings.add(new EventStanding(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getLong("gained")));
                }
            }
        }
        return standings;
    }

    private Optional<FederationEvent> querySingle(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql);
             ResultSet rs = select.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            long endedAt = rs.getLong("ended_at");
            Long endedAtOrNull = rs.wasNull() ? null : endedAt;
            return Optional.of(new FederationEvent(
                    rs.getLong("id"),
                    rs.getLong("starts_at"),
                    rs.getLong("ends_at"),
                    rs.getBoolean("ended"),
                    endedAtOrNull,
                    rs.getString("result")));
        }
    }
}
