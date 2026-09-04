package com.yeowool.core.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates the core tables on first startup. Deliberately not a migration
 * framework — v1 only ever adds columns/tables, and every statement is
 * idempotent ({@code IF NOT EXISTS}), which is enough while the schema is
 * this small. Revisit with a real migration tool once other Yeowool plugins
 * start adding their own tables against this same database.
 */
public final class SchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_players (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                on_balance BIGINT NOT NULL DEFAULT 0,
                bank_balance BIGINT NOT NULL DEFAULT 0,
                land_level INT NOT NULL DEFAULT 1,
                land_xp BIGINT NOT NULL DEFAULT 0,
                first_join BIGINT NOT NULL,
                last_seen BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_player_lands (
                uuid CHAR(36) NOT NULL,
                land_id CHAR(36) NOT NULL,
                PRIMARY KEY (uuid, land_id),
                FOREIGN KEY (uuid) REFERENCES yw_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_player_settings (
                uuid CHAR(36) NOT NULL,
                setting_key VARCHAR(64) NOT NULL,
                setting_value VARCHAR(255) NOT NULL,
                PRIMARY KEY (uuid, setting_key),
                FOREIGN KEY (uuid) REFERENCES yw_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_player_statistics (
                uuid CHAR(36) NOT NULL,
                stat_key VARCHAR(64) NOT NULL,
                stat_value BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, stat_key),
                FOREIGN KEY (uuid) REFERENCES yw_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_logs (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                plugin_name VARCHAR(64) NOT NULL,
                category VARCHAR(64) NOT NULL,
                actor CHAR(36) NULL,
                message VARCHAR(512) NOT NULL,
                data TEXT NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_category (category),
                INDEX idx_actor (actor)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_mailbox_items (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                recipient CHAR(36) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                source_plugin VARCHAR(64) NOT NULL,
                note VARCHAR(255) NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_recipient (recipient)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            // Mirrors yeowool-discord's DiscordSchemaInitializer (the table's "real" owner, since
            // yeowool-web's poller is what actually consumes it) - duplicated here, idempotently,
            // because yeowool-core's own MailboxManager writes into it directly and must not
            // depend on YeowoolDiscord happening to already be loaded/enabled first.
            """
            CREATE TABLE IF NOT EXISTS yw_discord_dm_queue (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                uuid CHAR(36) NOT NULL,
                message VARCHAR(512) NOT NULL,
                created_at BIGINT NOT NULL,
                processed_at BIGINT NULL,
                result VARCHAR(255) NULL,
                INDEX idx_unprocessed (processed_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_punishments (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                target CHAR(36) NOT NULL,
                type VARCHAR(16) NOT NULL,
                reason VARCHAR(255) NOT NULL,
                staff CHAR(36) NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NULL,
                active TINYINT NOT NULL DEFAULT 1,
                revoked_by CHAR(36) NULL,
                revoked_at BIGINT NULL,
                points INT NOT NULL DEFAULT 0,
                INDEX idx_target (target),
                INDEX idx_type_active (type, active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private SchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                for (String ddl : DDL) {
                    statement.executeUpdate(ddl);
                }
            }
            // Additive migration for servers whose yw_players predates cash_balance
            // (the premium currency). Checked via metadata + a plain ALTER instead of
            // "ADD COLUMN IF NOT EXISTS", which only MySQL 8.0.29+ supports.
            addColumnIfMissing(connection, "yw_players", "cash_balance", "BIGINT NOT NULL DEFAULT 0");
            // Additive migration for servers whose yw_punishments predates the
            // 경고 지급/회수 point-count system (누적 경고).
            addColumnIfMissing(connection, "yw_punishments", "points", "INT NOT NULL DEFAULT 0");
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
