package com.yeowool.teleport.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class TeleportSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_homes (
                uuid CHAR(36) NOT NULL,
                name VARCHAR(32) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                PRIMARY KEY (uuid, name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_warps (
                name VARCHAR(32) NOT NULL PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                created_by CHAR(36) NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_player_warps (
                owner CHAR(36) NOT NULL,
                name VARCHAR(32) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                created_at BIGINT NOT NULL,
                PRIMARY KEY (owner, name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_player_warp_ratings (
                owner CHAR(36) NOT NULL,
                warp_name VARCHAR(32) NOT NULL,
                rater CHAR(36) NOT NULL,
                rating INT NOT NULL,
                rated_at BIGINT NOT NULL,
                PRIMARY KEY (owner, warp_name, rater)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_player_warp_favorites (
                player CHAR(36) NOT NULL,
                owner CHAR(36) NOT NULL,
                warp_name VARCHAR(32) NOT NULL,
                favorited_at BIGINT NOT NULL,
                PRIMARY KEY (player, owner, warp_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private TeleportSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
            addColumnIfMissing(connection, "yw_player_warps", "display_name", "VARCHAR(64) NULL");
            addColumnIfMissing(connection, "yw_player_warps", "description", "VARCHAR(64) NULL");
            addColumnIfMissing(connection, "yw_player_warps", "category", "VARCHAR(32) NOT NULL DEFAULT 'none'");
            addColumnIfMissing(connection, "yw_player_warps", "status", "VARCHAR(16) NOT NULL DEFAULT 'OPENED'");
            addColumnIfMissing(connection, "yw_player_warps", "preview_item_id", "VARCHAR(128) NULL");
            addColumnIfMissing(connection, "yw_player_warps", "price", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "yw_player_warps", "visits", "BIGINT NOT NULL DEFAULT 0");
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
