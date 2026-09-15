package com.yeowool.community.ranking;

import com.yeowool.community.profile.PlaytimeTracker;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Raw JDBC reads against {@code yw_players}/{@code yw_player_statistics} —
 * needs every player regardless of whether their {@code PlayerData} is
 * currently cached, so this bypasses the normal player-data layer entirely.
 * Must only be called off the main thread.
 */
public final class RankingRepository {

    private final DataSource dataSource;

    public RankingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** {@code limit} should be larger than the leaderboard actually shown — {@link RankingManager} filters out OPs after this returns. */
    public List<RankingEntry> topByMoney(int limit) throws SQLException {
        return query("SELECT uuid, username, (on_balance + bank_balance) AS total FROM yw_players "
                + "ORDER BY total DESC LIMIT ?", limit);
    }

    public List<RankingEntry> topByLand(int limit) throws SQLException {
        return query("SELECT uuid, username, land_level AS total FROM yw_players "
                + "ORDER BY total DESC LIMIT ?", limit);
    }

    public List<RankingEntry> topByPlaytime(int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT p.uuid, p.username, s.stat_value AS total FROM yw_players p "
                             + "JOIN yw_player_statistics s ON s.uuid = p.uuid AND s.stat_key = ? "
                             + "ORDER BY s.stat_value DESC LIMIT ?")) {
            select.setString(1, PlaytimeTracker.STAT_KEY);
            select.setInt(2, limit);
            return readEntries(select);
        }
    }

    private List<RankingEntry> query(String sql, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, limit);
            return readEntries(select);
        }
    }

    private List<RankingEntry> readEntries(PreparedStatement select) throws SQLException {
        List<RankingEntry> entries = new ArrayList<>();
        try (ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                entries.add(new RankingEntry(UUID.fromString(rs.getString("uuid")), rs.getString("username"), rs.getLong("total")));
            }
        }
        return entries;
    }
}
