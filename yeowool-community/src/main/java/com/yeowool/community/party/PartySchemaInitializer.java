package com.yeowool.community.party;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Tables backing the cross-server party system ({@link PartyRepository}). */
public final class PartySchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_party (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(32) NOT NULL UNIQUE,
                leader_uuid CHAR(36) NOT NULL,
                max_size INT NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_party_member (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                party_id BIGINT NOT NULL,
                name VARCHAR(32) NOT NULL,
                joined_at BIGINT NOT NULL,
                INDEX idx_party_member_party (party_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_party_presence (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                server VARCHAR(32) NOT NULL,
                name VARCHAR(32) NOT NULL,
                health DOUBLE NOT NULL,
                max_health DOUBLE NOT NULL,
                updated_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private PartySchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
        }
    }
}
