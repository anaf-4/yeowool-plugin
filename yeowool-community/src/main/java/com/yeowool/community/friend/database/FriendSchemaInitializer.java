package com.yeowool.community.friend.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class FriendSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_friendships (
                uuid_a CHAR(36) NOT NULL,
                uuid_b CHAR(36) NOT NULL,
                created_at BIGINT NOT NULL,
                PRIMARY KEY (uuid_a, uuid_b),
                INDEX idx_uuid_a (uuid_a)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private FriendSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
