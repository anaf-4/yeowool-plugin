package com.yeowool.community.ranking.statue;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class RankingStatueSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_ranking_statues (
                category VARCHAR(16) NOT NULL,
                position TINYINT NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                npc_id INT NULL,
                PRIMARY KEY (category, position)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private RankingStatueSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
