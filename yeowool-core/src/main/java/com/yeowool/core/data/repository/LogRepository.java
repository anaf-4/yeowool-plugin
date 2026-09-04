package com.yeowool.core.data.repository;

import com.yeowool.core.api.model.LogEntry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Blocking insert into {@code yw_logs}. Data is flattened to a single
 * {@code key=value;key2=value2} string rather than JSON to avoid pulling in
 * a JSON library for what is, for now, simple diagnostic key/value pairs.
 */
public final class LogRepository {

    private final DataSource dataSource;

    public LogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(LogEntry entry) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO yw_logs (plugin_name, category, actor, message, data, created_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, entry.pluginName());
            statement.setString(2, entry.category());
            statement.setString(3, entry.actor() == null ? null : entry.actor().toString());
            statement.setString(4, entry.message());
            statement.setString(5, flatten(entry.data()));
            statement.setLong(6, entry.timestamp());
            statement.executeUpdate();
        }
    }

    private String flatten(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }
}
