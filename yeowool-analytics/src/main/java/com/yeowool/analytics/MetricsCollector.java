package com.yeowool.analytics;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregate queries backing {@code /여울통계} (plugin plan 11.2).
 * Deliberately queries YeowoolCore/YeowoolLand's tables by name rather than
 * taking a compile dependency on those plugins — Analytics only ever reads,
 * never writes, so this stays a soft coupling: if those schemas change,
 * update the SQL here to match.
 */
public final class MetricsCollector {

    public record EconomySummary(long totalOn, double averageOn, long totalBank, long playerCount) {
    }

    public record LandSummary(long landCount, long totalChunks, double averageLevel) {
    }

    private final DataSource dataSource;

    public MetricsCollector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public EconomySummary economySummary() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COALESCE(SUM(on_balance),0) AS total_on, COALESCE(AVG(on_balance),0) AS avg_on, "
                             + "COALESCE(SUM(bank_balance),0) AS total_bank, COUNT(*) AS player_count FROM yw_players")) {
            rs.next();
            return new EconomySummary(rs.getLong("total_on"), rs.getDouble("avg_on"), rs.getLong("total_bank"), rs.getLong("player_count"));
        }
    }

    public LandSummary landSummary() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            long landCount;
            long totalChunks;
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS c FROM yw_lands")) {
                rs.next();
                landCount = rs.getLong("c");
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS c FROM yw_land_chunks")) {
                rs.next();
                totalChunks = rs.getLong("c");
            }
            double avgLevel;
            try (ResultSet rs = statement.executeQuery("SELECT COALESCE(AVG(land_level),0) AS avg_level FROM yw_players")) {
                rs.next();
                avgLevel = rs.getDouble("avg_level");
            }
            return new LandSummary(landCount, totalChunks, avgLevel);
        }
    }

    public record RankingRow(String username, long value) {
    }

    /** {@code /랭킹 온} — top wallet balances. */
    public List<RankingRow> topOnBalance(int limit) throws SQLException {
        return topByColumn("on_balance", limit);
    }

    /** {@code /랭킹 은행} — top bank balances. */
    public List<RankingRow> topBankBalance(int limit) throws SQLException {
        return topByColumn("bank_balance", limit);
    }

    /** {@code /랭킹 토지레벨} — highest land level, ties broken by XP. */
    public List<RankingRow> topLandLevel(int limit) throws SQLException {
        List<RankingRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT username, land_level FROM yw_players ORDER BY land_level DESC, land_xp DESC LIMIT ?")) {
            select.setInt(1, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RankingRow(rs.getString("username"), rs.getLong("land_level")));
                }
            }
        }
        return rows;
    }

    private List<RankingRow> topByColumn(String column, int limit) throws SQLException {
        List<RankingRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT username, " + column + " AS value FROM yw_players ORDER BY " + column + " DESC LIMIT ?")) {
            select.setInt(1, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RankingRow(rs.getString("username"), rs.getLong("value")));
                }
            }
        }
        return rows;
    }

    /**
     * {@code /랭킹 낚시도감} — most distinct fish species caught, from
     * YeowoolLife's {@code life.fishing.catalog.<speciesId>} statistic keys.
     */
    public List<RankingRow> topFishingCatalog(int limit) throws SQLException {
        List<RankingRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT p.username AS username, COUNT(*) AS species_count FROM yw_player_statistics s "
                             + "JOIN yw_players p ON p.uuid = s.uuid "
                             + "WHERE s.stat_key LIKE 'life.fishing.catalog.%' AND s.stat_value > 0 "
                             + "GROUP BY s.uuid, p.username ORDER BY species_count DESC LIMIT ?")) {
            select.setInt(1, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RankingRow(rs.getString("username"), rs.getLong("species_count")));
                }
            }
        }
        return rows;
    }

    /**
     * {@code /시즌랭킹} — top {@code season.score} (see
     * {@code SeasonScoreListener}), a resettable proxy for "how active this
     * season" built from land-XP gains rather than real economy/land data.
     */
    public List<RankingRow> topSeasonScore(int limit) throws SQLException {
        List<RankingRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT p.username AS username, s.stat_value AS value FROM yw_player_statistics s "
                             + "JOIN yw_players p ON p.uuid = s.uuid "
                             + "WHERE s.stat_key = 'season.score' AND s.stat_value > 0 "
                             + "ORDER BY s.stat_value DESC LIMIT ?")) {
            select.setInt(1, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RankingRow(rs.getString("username"), rs.getLong("value")));
                }
            }
        }
        return rows;
    }

    /**
     * Sums every {@code yw_player_statistics} row whose key starts with
     * {@code life.}, grouped by key — e.g. {@code life.farming.harvested -> 12345}.
     */
    public Map<String, Long> lifeStatistics() throws SQLException {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT stat_key, SUM(stat_value) AS total FROM yw_player_statistics "
                             + "WHERE stat_key LIKE 'life.%' GROUP BY stat_key ORDER BY stat_key")) {
            while (rs.next()) {
                result.put(rs.getString("stat_key"), rs.getLong("total"));
            }
        }
        return result;
    }
}
