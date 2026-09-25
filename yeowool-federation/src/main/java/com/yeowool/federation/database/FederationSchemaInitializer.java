package com.yeowool.federation.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Creates YeowoolFederation's own tables on the shared database. Idempotent DDL, same approach as every other Yeowool module's schema initializer. */
public final class FederationSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_federations (
                id CHAR(36) NOT NULL PRIMARY KEY,
                name VARCHAR(32) NOT NULL UNIQUE,
                description VARCHAR(255) NULL,
                level INT NOT NULL DEFAULT 1,
                leader_land_id CHAR(36) NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_federation_members (
                federation_id CHAR(36) NOT NULL,
                land_id CHAR(36) NOT NULL PRIMARY KEY,
                role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                joined_at BIGINT NOT NULL,
                INDEX idx_federation (federation_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_federation_applications (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                federation_id CHAR(36) NOT NULL,
                land_id CHAR(36) NOT NULL,
                applied_at BIGINT NOT NULL,
                UNIQUE KEY uniq_pending (federation_id, land_id),
                INDEX idx_federation (federation_id),
                INDEX idx_land (land_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private FederationSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
            addColumnIfMissing(connection, "yw_federations", "bank_balance", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "yw_federations", "activity", "BIGINT NOT NULL DEFAULT 0");
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
