package com.yeowool.admin.starterkit;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class StarterKitSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_starter_kit_items (
                slot INT NOT NULL PRIMARY KEY,
                item_data MEDIUMTEXT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private StarterKitSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
