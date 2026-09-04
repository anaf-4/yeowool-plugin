package com.yeowool.life.farming.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One row per crop currently growing under the fixed 10-minute timer (see
 * {@link com.yeowool.life.farming.CropTimerService}), so an in-progress crop
 * survives a server restart instead of getting stuck at its current growth
 * stage forever.
 */
public final class FarmingSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_farm_crops (
                world VARCHAR(64) NOT NULL,
                x INT NOT NULL,
                y INT NOT NULL,
                z INT NOT NULL,
                finish_at BIGINT NOT NULL,
                PRIMARY KEY (world, x, y, z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private FarmingSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
