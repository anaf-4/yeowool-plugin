package com.yeowool.core.data.repository;

import com.yeowool.core.api.model.PlayerData;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_players} and its related tables. Every
 * method here does real I/O and must only be called from
 * {@link com.yeowool.core.database.DatabaseManager#getExecutor()}, never the
 * main server thread.
 */
public final class PlayerRepository {

    private final DataSource dataSource;

    public PlayerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PlayerData findOrCreate(UUID uuid, String username) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            PlayerData existing = findBase(connection, uuid);
            if (existing != null) {
                if (!existing.getUsername().equals(username)) {
                    existing.setUsername(username);
                }
                loadRelated(connection, existing);
                existing.clearDirty();
                return existing;
            }

            long now = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO yw_players (uuid, username, on_balance, bank_balance, cash_balance, land_level, land_xp, first_join, last_seen) "
                            + "VALUES (?, ?, 0, 0, 0, 1, 0, ?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, username);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
            }
            return new PlayerData(uuid, username, now, now);
        }
    }

    private PlayerData findBase(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT username, on_balance, bank_balance, cash_balance, land_level, land_xp, first_join, last_seen "
                        + "FROM yw_players WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PlayerData data = new PlayerData(uuid, rs.getString("username"), rs.getLong("first_join"), rs.getLong("last_seen"));
                data.setOnBalance(rs.getLong("on_balance"));
                data.setBankBalance(rs.getLong("bank_balance"));
                data.setCashBalance(rs.getLong("cash_balance"));
                data.setLandLevel(rs.getInt("land_level"));
                data.addLandXp(rs.getLong("land_xp"));
                return data;
            }
        }
    }

    private void loadRelated(Connection connection, PlayerData data) throws SQLException {
        Set<UUID> landIds = new HashSet<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT land_id FROM yw_player_lands WHERE uuid = ?")) {
            select.setString(1, data.getUuid().toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    landIds.add(UUID.fromString(rs.getString("land_id")));
                }
            }
        }
        data.restoreLandIds(landIds);

        Map<String, String> settings = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT setting_key, setting_value FROM yw_player_settings WHERE uuid = ?")) {
            select.setString(1, data.getUuid().toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
                }
            }
        }
        data.restoreSettings(settings);

        Map<String, Long> statistics = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT stat_key, stat_value FROM yw_player_statistics WHERE uuid = ?")) {
            select.setString(1, data.getUuid().toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    statistics.put(rs.getString("stat_key"), rs.getLong("stat_value"));
                }
            }
        }
        data.restoreStatistics(statistics);
    }

    public void save(PlayerData data) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO yw_players (uuid, username, on_balance, bank_balance, cash_balance, land_level, land_xp, first_join, last_seen) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE username = VALUES(username), on_balance = VALUES(on_balance), "
                                + "bank_balance = VALUES(bank_balance), cash_balance = VALUES(cash_balance), "
                                + "land_level = VALUES(land_level), land_xp = VALUES(land_xp), "
                                + "last_seen = VALUES(last_seen)")) {
                    update.setString(1, data.getUuid().toString());
                    update.setString(2, data.getUsername());
                    update.setLong(3, data.getOnBalance());
                    update.setLong(4, data.getBankBalance());
                    update.setLong(5, data.getCashBalance());
                    update.setInt(6, data.getLandLevel());
                    update.setLong(7, data.getLandXp());
                    update.setLong(8, data.getFirstJoin());
                    update.setLong(9, data.getLastSeen());
                    update.executeUpdate();
                }

                replaceLandIds(connection, data);
                replaceSettings(connection, data);
                replaceStatistics(connection, data);

                connection.commit();
                data.clearDirty();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void replaceLandIds(Connection connection, PlayerData data) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_player_lands WHERE uuid = ?")) {
            delete.setString(1, data.getUuid().toString());
            delete.executeUpdate();
        }
        Set<UUID> landIds = data.getLandIds();
        if (landIds.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO yw_player_lands (uuid, land_id) VALUES (?, ?)")) {
            for (UUID landId : landIds) {
                insert.setString(1, data.getUuid().toString());
                insert.setString(2, landId.toString());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceSettings(Connection connection, PlayerData data) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_player_settings WHERE uuid = ?")) {
            delete.setString(1, data.getUuid().toString());
            delete.executeUpdate();
        }
        Map<String, String> settings = data.exportSettings();
        if (settings.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO yw_player_settings (uuid, setting_key, setting_value) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                insert.setString(1, data.getUuid().toString());
                insert.setString(2, entry.getKey());
                insert.setString(3, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceStatistics(Connection connection, PlayerData data) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_player_statistics WHERE uuid = ?")) {
            delete.setString(1, data.getUuid().toString());
            delete.executeUpdate();
        }
        Map<String, Long> statistics = data.exportStatistics();
        if (statistics.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO yw_player_statistics (uuid, stat_key, stat_value) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, Long> entry : statistics.entrySet()) {
                insert.setString(1, data.getUuid().toString());
                insert.setString(2, entry.getKey());
                insert.setLong(3, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
