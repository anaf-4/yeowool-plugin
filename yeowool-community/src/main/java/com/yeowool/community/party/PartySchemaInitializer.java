package com.yeowool.community.party;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
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
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_party_join_request (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                party_id BIGINT NOT NULL,
                name VARCHAR(32) NOT NULL,
                requested_at BIGINT NOT NULL,
                INDEX idx_party_join_request_party (party_id)
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
            // Additive migration for servers whose yw_party predates the
            // 자유가입/신청승인 join-mode choice — defaults every existing party to
            // FREE (today's behavior). "ADD COLUMN IF NOT EXISTS" only works on
            // MySQL 8.0.29+, so this checks metadata instead (see YeowoolCore's
            // SchemaInitializer for the same pattern).
            addColumnIfMissing(connection, "yw_party", "join_mode", "VARCHAR(16) NOT NULL DEFAULT 'FREE'");
        }
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
