package com.yeowool.life.scrapyard;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Tables backing the "여울 폐기장" minigame's admin-registered world locations ({@link ScrapyardLocationStore}). */
public final class ScrapyardSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_scrapyard_point (
                category VARCHAR(32) NOT NULL PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL DEFAULT 0,
                pitch FLOAT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_scrapyard_chest (
                id INT AUTO_INCREMENT PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x INT NOT NULL,
                y INT NOT NULL,
                z INT NOT NULL,
                last_opened_date VARCHAR(10)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_scrapyard_mob_spawn (
                id INT AUTO_INCREMENT PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private ScrapyardSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
            // category was originally VARCHAR(16), too short for "entry_destination" (18 chars) —
            // widen it for anyone who already had the table created before this fix.
            statement.executeUpdate("ALTER TABLE yw_scrapyard_point MODIFY COLUMN category VARCHAR(32) NOT NULL");
        }
    }
}
