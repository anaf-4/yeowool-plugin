package com.yeowool.admin.banneditem.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BannedItemSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_banned_items (
                material VARCHAR(64) NOT NULL,
                ban_type VARCHAR(16) NOT NULL,
                banned_by VARCHAR(32) NOT NULL,
                banned_at BIGINT NOT NULL,
                PRIMARY KEY (material, ban_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private BannedItemSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
