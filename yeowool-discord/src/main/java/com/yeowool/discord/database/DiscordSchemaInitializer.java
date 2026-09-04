package com.yeowool.discord.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Tables shared with the standalone JS Discord bot over the same MySQL schema the game server
 * already uses (config/warn-queue/announcements), plus two newer ones owned by {@code yeowool-web}
 * instead: {@code yw_account_links} (Discord↔Minecraft account linking, written by the website when
 * a player links their account on {@code /mypage}) and {@code yw_discord_dm_queue} (outbound DM
 * requests — game plugins insert rows when a mailbox item arrives or an auction sells, the website's
 * own background poller resolves the recipient via {@code yw_account_links} and sends the actual
 * Discord DM, since only the website currently holds real Discord API-calling code — see
 * yeowool-web/src/lib/discordDm.ts).
 */
public final class DiscordSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_discord_config (
                config_key VARCHAR(64) NOT NULL PRIMARY KEY,
                config_value TEXT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_discord_warn_queue (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                target_name VARCHAR(16) NOT NULL,
                reason VARCHAR(255) NOT NULL,
                points INT NOT NULL,
                requested_by VARCHAR(64) NOT NULL,
                created_at BIGINT NOT NULL,
                processed_at BIGINT NULL,
                result VARCHAR(255) NULL,
                action VARCHAR(16) NOT NULL DEFAULT 'GRANT',
                INDEX idx_unprocessed (processed_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_discord_announcements (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                message VARCHAR(512) NOT NULL,
                created_at BIGINT NOT NULL,
                relayed TINYINT NOT NULL DEFAULT 0,
                INDEX idx_unrelayed (relayed)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_account_links (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                discord_id VARCHAR(32) NOT NULL,
                username VARCHAR(16) NOT NULL,
                linked_at BIGINT NOT NULL,
                UNIQUE KEY idx_discord_id (discord_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
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
            """
    );

    private DiscordSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                for (String ddl : DDL) {
                    statement.executeUpdate(ddl);
                }
            }
            // 이 테이블이 action 컬럼 없이 이미 만들어져 있던 서버를 위한 추가 마이그레이션
            // (경고 회수 기능을 나중에 추가하면서 생김).
            addColumnIfMissing(connection, "yw_discord_warn_queue", "action", "VARCHAR(16) NOT NULL DEFAULT 'GRANT'");
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
