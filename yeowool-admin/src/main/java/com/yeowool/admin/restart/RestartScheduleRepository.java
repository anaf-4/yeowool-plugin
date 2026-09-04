package com.yeowool.admin.restart;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class RestartScheduleRepository {

    private final DataSource dataSource;

    public RestartScheduleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** {@code null} if this server has never had a schedule saved (caller falls back to config.yml). */
    public String load(String serverId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT restart_times FROM yw_server_restart_schedule WHERE server_id = ?")) {
            select.setString(1, serverId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public void save(String serverId, String restartTimesCsv) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_server_restart_schedule (server_id, restart_times) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE restart_times = VALUES(restart_times)")) {
            upsert.setString(1, serverId);
            upsert.setString(2, restartTimesCsv);
            upsert.executeUpdate();
        }
    }
}
