package com.yeowool.community.quest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cross-player "총 완료 퀘스트 수" ranking, backing {@link QuestLeaderboardGui}
 * — same raw-SQL-over-{@code core.dataSource()} pattern as
 * {@code yeowool-analytics}'s {@code MetricsCollector} (soft-coupled to
 * YeowoolCore's {@code yw_players}/{@code yw_player_statistics} tables by
 * name rather than a compile dependency). Always run off the main thread.
 */
public final class QuestLeaderboardQuery {

    public record Row(UUID uuid, String username, long total) {
    }

    private final DataSource dataSource;

    public QuestLeaderboardQuery(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Row> top(int limit) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT p.uuid AS uuid, p.username AS username, s.stat_value AS value FROM yw_player_statistics s "
                             + "JOIN yw_players p ON p.uuid = s.uuid "
                             + "WHERE s.stat_key = ? AND s.stat_value > 0 "
                             + "ORDER BY s.stat_value DESC LIMIT ?")) {
            select.setString(1, QuestManager.TOTAL_COMPLETED_STAT_KEY);
            select.setInt(2, limit);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(UUID.fromString(rs.getString("uuid")), rs.getString("username"), rs.getLong("value")));
                }
            }
        }
        return rows;
    }
}
