package com.yeowool.discord.verify;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking JDBC access for the {@code /인증} ↔ {@code /인증코드} account-link
 * flow. {@code yw_account_links} already exists (written by the website's
 * {@code /mypage} linking flow too — see {@code DiscordSchemaInitializer});
 * this just adds a second way to populate the same table. Must only be
 * called off the main thread.
 */
public final class VerifyRepository {

    /** A still-valid (unused, unexpired) code from the bot's {@code /인증} command. */
    public record PendingVerification(String discordId, String discordUsername) {
    }

    private final DataSource dataSource;

    public VerifyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<PendingVerification> findValidCode(String code) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT discord_id, discord_username FROM yw_discord_verify_codes "
                             + "WHERE code = ? AND used_at IS NULL AND expires_at > ?")) {
            select.setString(1, code);
            select.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PendingVerification(rs.getString("discord_id"), rs.getString("discord_username")));
            }
        }
    }

    public void markCodeUsed(String code) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_discord_verify_codes SET used_at = ? WHERE code = ?")) {
            update.setLong(1, System.currentTimeMillis());
            update.setString(2, code);
            update.executeUpdate();
        }
    }

    /** Empty if that Discord account isn't linked to anyone (yet). */
    public Optional<UUID> uuidLinkedTo(String discordId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid FROM yw_account_links WHERE discord_id = ?")) {
            select.setString(1, discordId);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString("uuid"))) : Optional.empty();
            }
        }
    }

    /** Replaces {@code uuid}'s link entirely (re-linking to a new Discord account is allowed). */
    public void link(UUID uuid, String discordId, String minecraftUsername) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_account_links (uuid, discord_id, username, linked_at) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE discord_id = VALUES(discord_id), username = VALUES(username), linked_at = VALUES(linked_at)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, discordId);
            upsert.setString(3, minecraftUsername);
            upsert.setLong(4, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }

    /** {@code minecraftUsername} lets the bot include the account name in the "연동 완료" DM it sends after granting the role. */
    public void enqueueRoleGrant(String discordId, String roleId, String minecraftUsername) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_discord_role_grant_queue (discord_id, role_id, minecraft_username, created_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, discordId);
            insert.setString(2, roleId);
            insert.setString(3, minecraftUsername);
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }
}
