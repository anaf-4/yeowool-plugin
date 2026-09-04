package com.yeowool.life.logging.tree.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One row per sapling currently growing on a fixed timer (see
 * {@link com.yeowool.life.logging.tree.TreeTimerService}), so a
 * server restart doesn't leave it frozen mid-growth forever — same
 * reasoning as {@link com.yeowool.life.farming.database.FarmingSchemaInitializer}.
 */
public final class TreeSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_tree_saplings (
                world VARCHAR(64) NOT NULL,
                x INT NOT NULL,
                y INT NOT NULL,
                z INT NOT NULL,
                finish_at BIGINT NOT NULL,
                PRIMARY KEY (world, x, y, z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private TreeSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
