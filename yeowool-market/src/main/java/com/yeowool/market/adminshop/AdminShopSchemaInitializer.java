package com.yeowool.market.adminshop;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Tables backing in-game-created NPC shops ({@code /상점생성} etc, see {@link AdminShopStore}). */
public final class AdminShopSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_admin_shops (
                id VARCHAR(32) NOT NULL PRIMARY KEY,
                title VARCHAR(64) NOT NULL,
                mode VARCHAR(16) NOT NULL,
                page_count INT NOT NULL DEFAULT 1,
                rotation_slots VARCHAR(255) NOT NULL DEFAULT '',
                rotation_interval_minutes INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_admin_shop_items (
                shop_id VARCHAR(32) NOT NULL,
                page INT NOT NULL,
                slot INT NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                PRIMARY KEY (shop_id, page, slot),
                FOREIGN KEY (shop_id) REFERENCES yw_admin_shops(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private AdminShopSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                for (String ddl : DDL) {
                    statement.executeUpdate(ddl);
                }
            }
            // Additive migration for servers whose yw_admin_shops predates rotation support.
            addColumnIfMissing(connection, "yw_admin_shops", "rotation_slots", "VARCHAR(255) NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "yw_admin_shops", "rotation_interval_minutes", "INT NOT NULL DEFAULT 0");
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
