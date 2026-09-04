package com.yeowool.discord;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Pushes this plugin's {@code config.yml} into {@code yw_discord_config}
 * (a plain key/value table) so the standalone JS bot — which has no access
 * to this server's local filesystem — can read its own settings from the
 * same MySQL database it already needs for the warn-queue/announcement
 * tables. Re-run any time (on enable, and via {@code /디스코드리로드}); each
 * key is just upserted, never partially written.
 *
 * <p>{@code bot-token} is encrypted with {@link ConfigCrypto} before being written, IF {@code
 * config-encryption-key} is set — otherwise it's written in plaintext exactly as before, so
 * existing setups keep working until an admin opts in by generating a key and copying it to the
 * bot's own environment. This exists because the DB's read-only web user has a blanket SELECT
 * grant across the whole schema (needed for the website's own read-only pages), which would
 * otherwise let anything with that connection string read the live Discord bot token in plain
 * text straight out of this table.
 */
final class DiscordConfigSync {

    private DiscordConfigSync() {
    }

    static void push(DataSource dataSource, FileConfiguration config, JavaPlugin plugin) throws SQLException {
        String encryptionKey = config.getString("config-encryption-key", "");
        String botToken = config.getString("bot-token", "");
        if (!encryptionKey.isBlank() && !botToken.isBlank()) {
            botToken = ConfigCrypto.encrypt(botToken, encryptionKey);
        } else if (botToken.isBlank()) {
            // nothing to warn about - no token configured yet either way
        } else {
            plugin.getLogger().warning("config-encryption-key가 설정되지 않아 bot-token이 DB에 평문으로 저장됩니다. "
                    + "config.yml에서 32바이트(base64) 키를 생성해 config-encryption-key에 넣고, "
                    + "같은 키를 JS 봇 쪽 환경변수(CONFIG_ENCRYPTION_KEY)에도 넣어주세요.");
        }

        try (Connection connection = dataSource.getConnection()) {
            set(connection, "bot-token", botToken);
            set(connection, "guild-id", config.getString("guild-id", ""));
            set(connection, "warn-log-channel-id", config.getString("warn-log-channel-id", ""));
            set(connection, "announcement-channel-id", config.getString("announcement-channel-id", ""));
            set(connection, "staff-role-id", config.getString("staff-role-id", ""));
            set(connection, "ticket-categories", ticketCategoriesJson(config));
        }
    }

    @SuppressWarnings("unchecked")
    private static String ticketCategoriesJson(FileConfiguration config) {
        List<Map<?, ?>> raw = config.getMapList("ticket-categories");
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> entry = (Map<String, Object>) raw.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{")
                    .append("\"id\":\"").append(escape(entry.get("id"))).append("\",")
                    .append("\"label\":\"").append(escape(entry.get("label"))).append("\",")
                    .append("\"emoji\":\"").append(escape(entry.get("emoji"))).append("\",")
                    .append("\"categoryId\":\"").append(escape(entry.get("category-id"))).append("\"")
                    .append("}");
        }
        json.append("]");
        return json.toString();
    }

    private static String escape(Object value) {
        return value == null ? "" : value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void set(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO yw_discord_config (config_key, config_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)")) {
            upsert.setString(1, key);
            upsert.setString(2, value == null ? "" : value);
            upsert.executeUpdate();
        }
    }
}
