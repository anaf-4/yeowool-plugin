package com.yeowool.admin.raffle.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class RaffleSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_raffle_wins (
                item_id VARCHAR(128) NOT NULL,
                uuid CHAR(36) NOT NULL,
                win_count INT NOT NULL DEFAULT 0,
                PRIMARY KEY (item_id, uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private RaffleSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
