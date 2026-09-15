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
            """,
            // 디스코드 /인증 슬래시 명령어가 써두는 1회용 코드. 게임의 /인증코드가 이걸
            // 확인해서 yw_account_links에 연동을 기록한다 - see VerifyCommand.
            """
            CREATE TABLE IF NOT EXISTS yw_discord_verify_codes (
                code CHAR(4) NOT NULL PRIMARY KEY,
                discord_id VARCHAR(32) NOT NULL,
                discord_username VARCHAR(64) NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                used_at BIGINT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            // 게임 → 디스코드 방향: /인증코드로 연동이 확정되면 여기에 역할 부여 요청을
            // 남기고, JS 봇이 폴링해서 실제로 역할을 지급한다 (warn_queue와 같은 패턴).
            """
            CREATE TABLE IF NOT EXISTS yw_discord_role_grant_queue (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                discord_id VARCHAR(32) NOT NULL,
                role_id VARCHAR(32) NOT NULL,
                minecraft_username VARCHAR(16) NOT NULL DEFAULT '',
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
            // 이 테이블이 minecraft_username 컬럼 없이 이미 만들어져 있던 서버를 위한 추가
            // 마이그레이션 (역할 지급 완료 DM에 계정 이름을 넣기 위해 나중에 추가됨).
            addColumnIfMissing(connection, "yw_discord_role_grant_queue", "minecraft_username", "VARCHAR(16) NOT NULL DEFAULT ''");
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
