package com.yeowool.admin.restart;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Backs {@link RestartScheduleStore} — one row per server (see {@code restart.this-server-id}). */
public final class RestartScheduleSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_server_restart_schedule (
                server_id VARCHAR(32) NOT NULL PRIMARY KEY,
                restart_times VARCHAR(255) NOT NULL DEFAULT ''
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private RestartScheduleSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
