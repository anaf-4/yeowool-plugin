package com.yeowool.land.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates YeowoolLand's own tables on the shared database (obtained via
 * {@link com.yeowool.core.api.YeowoolCoreAPI#dataSource()}). Same
 * idempotent-DDL approach as YeowoolCore's own SchemaInitializer.
 */
public final class LandSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_lands (
                id CHAR(36) NOT NULL PRIMARY KEY,
                owner_uuid CHAR(36) NOT NULL,
                name VARCHAR(32) NULL,
                pvp_enabled TINYINT(1) NOT NULL DEFAULT 0,
                bank_balance BIGINT NOT NULL DEFAULT 0,
                created_at BIGINT NOT NULL,
                INDEX idx_owner (owner_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_land_chunks (
                land_id CHAR(36) NOT NULL,
                world VARCHAR(64) NOT NULL,
                chunk_x INT NOT NULL,
                chunk_z INT NOT NULL,
                PRIMARY KEY (world, chunk_x, chunk_z),
                FOREIGN KEY (land_id) REFERENCES yw_lands(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_land_members (
                land_id CHAR(36) NOT NULL,
                member_uuid CHAR(36) NOT NULL,
                can_build TINYINT(1) NOT NULL DEFAULT 1,
                can_containers TINYINT(1) NOT NULL DEFAULT 1,
                PRIMARY KEY (land_id, member_uuid),
                FOREIGN KEY (land_id) REFERENCES yw_lands(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_land_barrels (
                world VARCHAR(64) NOT NULL,
                x INT NOT NULL,
                y INT NOT NULL,
                z INT NOT NULL,
                PRIMARY KEY (world, x, y, z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private LandSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                for (String ddl : DDL) {
                    statement.executeUpdate(ddl);
                }
            }
            // Additive migration for servers whose yw_lands predates bank_balance.
            // Checked via metadata + a plain ALTER instead of "ADD COLUMN IF NOT EXISTS",
            // which only MySQL 8.0.29+ supports (older MySQL/MariaDB reject the syntax).
            addColumnIfMissing(connection, "yw_lands", "bank_balance", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "yw_lands", "name", "VARCHAR(32) NULL");
            addColumnIfMissing(connection, "yw_land_members", "can_build", "TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(connection, "yw_land_members", "can_containers", "TINYINT(1) NOT NULL DEFAULT 1");
        }
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return; // already has the column
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
