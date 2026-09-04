package com.yeowool.admin.logviewer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read side of {@code yw_logs} for {@code /여울관리 로그}. Every plugin writes
 * to this table through {@code core.logs()}; this is the first in-game way to
 * read it back instead of querying MySQL by hand.
 */
public final class LogQueryService {

    private final DataSource dataSource;

    public LogQueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record LogRow(String pluginName, String category, UUID actor, String message, String data, long createdAt) {
    }

    public List<LogRow> queryByActor(UUID actor, String category, int limit) throws SQLException {
        String sql = "SELECT plugin_name, category, actor, message, data, created_at FROM yw_logs WHERE actor = ?"
                + (category != null ? " AND category = ?" : "")
                + " ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, actor.toString());
            if (category != null) {
                statement.setString(index++, category);
            }
            statement.setInt(index, limit);
            return read(statement);
        }
    }

    private List<LogRow> read(PreparedStatement statement) throws SQLException {
        List<LogRow> rows = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String actorStr = rs.getString("actor");
                rows.add(new LogRow(
                        rs.getString("plugin_name"),
                        rs.getString("category"),
                        actorStr == null ? null : UUID.fromString(actorStr),
                        rs.getString("message"),
                        rs.getString("data"),
                        rs.getLong("created_at")
                ));
            }
        }
        return rows;
    }
}
