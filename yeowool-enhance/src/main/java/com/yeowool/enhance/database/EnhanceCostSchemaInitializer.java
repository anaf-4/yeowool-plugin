package com.yeowool.enhance.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class EnhanceCostSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_enhance_costs (
                level INT NOT NULL PRIMARY KEY,
                currency BIGINT NOT NULL,
                material_id VARCHAR(128) NOT NULL,
                material_amount INT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private EnhanceCostSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
