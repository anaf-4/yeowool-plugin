package com.yeowool.life.bag.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BagSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_player_bags (
                uuid CHAR(36) NOT NULL,
                bag_type VARCHAR(16) NOT NULL,
                capacity INT NOT NULL DEFAULT 18,
                contents MEDIUMTEXT,
                PRIMARY KEY (uuid, bag_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private BagSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
