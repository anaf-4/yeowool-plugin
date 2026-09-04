package com.yeowool.discord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Blocking JDBC access to {@code yw_discord_warn_queue} — see {@link DiscordWarnQueuePoller}. */
final class DiscordWarnQueueRepository {

    /** {@code action} is {@code "GRANT"} or {@code "REVOKE"} — see {@link DiscordWarnQueuePoller#process}. */
    record QueuedWarn(long id, String targetName, String reason, int points, String requestedBy, String action) {
    }

    private final DataSource dataSource;

    DiscordWarnQueueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<QueuedWarn> findUnprocessed() throws SQLException {
        List<QueuedWarn> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, target_name, reason, points, requested_by, action FROM yw_discord_warn_queue "
                             + "WHERE processed_at IS NULL ORDER BY id ASC LIMIT 20")) {
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    results.add(new QueuedWarn(rs.getLong("id"), rs.getString("target_name"),
                            rs.getString("reason"), rs.getInt("points"), rs.getString("requested_by"), rs.getString("action")));
                }
            }
        }
        return results;
    }

    void markProcessed(long id, String result) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_discord_warn_queue SET processed_at = ?, result = ? WHERE id = ?")) {
            update.setLong(1, System.currentTimeMillis());
            if (result == null) {
                update.setNull(2, Types.VARCHAR);
            } else {
                update.setString(2, result);
            }
            update.setLong(3, id);
            update.executeUpdate();
        }
    }
}
