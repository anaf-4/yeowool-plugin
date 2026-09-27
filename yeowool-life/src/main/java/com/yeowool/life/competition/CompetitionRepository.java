package com.yeowool.life.competition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shared competition rows and cross-server scores. Payout is claimed by one conditional UPDATE. */
public final class CompetitionRepository {

    public record Standing(UUID player, String name, long score) {
    }

    public record Competition(String dateKey, String activity) {
    }

    public record Finished(String dateKey, String activity, long paidAt) {
    }

    public record ScoreDelta(String dateKey, UUID player, String name, long delta) {
    }

    private static final String COMPETITIONS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_life_competitions (
                comp_date VARCHAR(10) NOT NULL PRIMARY KEY,
                activity VARCHAR(16) NOT NULL,
                ends_at BIGINT NOT NULL,
                paid TINYINT(1) NOT NULL DEFAULT 0,
                paid_at BIGINT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String SCORES_DDL = """
            CREATE TABLE IF NOT EXISTS yw_life_competition_scores (
                comp_date VARCHAR(10) NOT NULL,
                player CHAR(36) NOT NULL,
                name VARCHAR(16) NOT NULL,
                score BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (comp_date, player),
                INDEX idx_date_score (comp_date, score)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public CompetitionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(COMPETITIONS_DDL);
            statement.executeUpdate(SCORES_DDL);
        }
    }

    public void ensureCompetition(String dateKey, String activity, long endsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_life_competitions (comp_date, activity, ends_at) VALUES (?, ?, ?)")) {
            ps.setString(1, dateKey);
            ps.setString(2, activity);
            ps.setLong(3, endsAt);
            ps.executeUpdate();
        }
    }

    public void addScores(List<ScoreDelta> deltas) throws SQLException {
        if (deltas.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_life_competition_scores (comp_date, player, name, score) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE score = score + VALUES(score), name = VALUES(name)")) {
            for (ScoreDelta delta : deltas) {
                ps.setString(1, delta.dateKey());
                ps.setString(2, delta.player().toString());
                ps.setString(3, delta.name());
                ps.setLong(4, delta.delta());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Standing> top(String dateKey, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player, name, score FROM yw_life_competition_scores WHERE comp_date = ? "
                             + "ORDER BY score DESC, player LIMIT ?")) {
            ps.setString(1, dateKey);
            ps.setInt(2, limit);
            List<Standing> standings = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    standings.add(new Standing(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                }
            }
            return standings;
        }
    }

    public long scoreOf(String dateKey, UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT score FROM yw_life_competition_scores WHERE comp_date = ? AND player = ?")) {
            ps.setString(1, dateKey);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public List<Competition> unpaidEnded(long endedBefore) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT comp_date, activity FROM yw_life_competitions WHERE paid = 0 AND ends_at <= ?")) {
            ps.setLong(1, endedBefore);
            List<Competition> competitions = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    competitions.add(new Competition(rs.getString(1), rs.getString(2)));
                }
            }
            return competitions;
        }
    }

    /** True only for the one caller that flips this ended competition to paid. */
    public boolean claimPayout(String dateKey, long endedBefore, long now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_life_competitions SET paid = 1, paid_at = ? WHERE comp_date = ? AND paid = 0 AND ends_at <= ?")) {
            ps.setLong(1, now);
            ps.setString(2, dateKey);
            ps.setLong(3, endedBefore);
            return ps.executeUpdate() == 1;
        }
    }

    public List<Finished> paidSince(long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT comp_date, activity, paid_at FROM yw_life_competitions WHERE paid = 1 AND paid_at > ? ORDER BY paid_at")) {
            ps.setLong(1, since);
            List<Finished> finished = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    finished.add(new Finished(rs.getString(1), rs.getString(2), rs.getLong(3)));
                }
            }
            return finished;
        }
    }
}
