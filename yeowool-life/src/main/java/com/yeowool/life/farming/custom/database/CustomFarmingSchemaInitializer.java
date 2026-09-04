package com.yeowool.life.farming.custom.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One row per custom crop currently mid-growth: which crop, which stage
 * it's currently sitting at, and when it advances to the next one — so a
 * server restart doesn't freeze it at the current stage forever (same
 * reasoning as {@link com.yeowool.life.farming.database.FarmingSchemaInitializer}
 * for vanilla crops).
 */
public final class CustomFarmingSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_custom_farm_crops (
                world VARCHAR(64) NOT NULL,
                x INT NOT NULL,
                y INT NOT NULL,
                z INT NOT NULL,
                crop_id VARCHAR(64) NOT NULL,
                current_stage INT NOT NULL,
                finish_at BIGINT NOT NULL,
                PRIMARY KEY (world, x, y, z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private CustomFarmingSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
