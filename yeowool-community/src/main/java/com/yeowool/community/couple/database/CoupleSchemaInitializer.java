package com.yeowool.community.couple.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CoupleSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_couples (
                uuid_a CHAR(36) NOT NULL,
                uuid_b CHAR(36) NOT NULL,
                since BIGINT NOT NULL,
                PRIMARY KEY (uuid_a, uuid_b),
                INDEX idx_uuid_a (uuid_a)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private CoupleSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
