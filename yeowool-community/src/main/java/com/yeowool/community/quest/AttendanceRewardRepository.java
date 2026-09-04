package com.yeowool.community.quest;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AttendanceRewardRepository {

    public record ConfigRow(long amount, CurrencyType currency) {
    }

    private final DataSource dataSource;

    public AttendanceRewardRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, ConfigRow> loadConfig() throws SQLException {
        Map<String, ConfigRow> rows = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT tier, amount, currency FROM yw_attendance_reward_config");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                CurrencyType currency;
                try {
                    currency = CurrencyType.valueOf(rs.getString("currency"));
                } catch (IllegalArgumentException e) {
                    currency = CurrencyType.ON;
                }
                rows.put(rs.getString("tier"), new ConfigRow(rs.getLong("amount"), currency));
            }
        }
        return rows;
    }

    public void saveConfig(String tier, long amount, CurrencyType currency) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_attendance_reward_config (tier, amount, currency) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE amount = VALUES(amount), currency = VALUES(currency)")) {
            insert.setString(1, tier);
            insert.setLong(2, amount);
            insert.setString(3, currency.name());
            insert.executeUpdate();
        }
    }

    public Map<String, Map<Integer, ItemStack>> loadItems() throws SQLException {
        Map<String, Map<Integer, ItemStack>> byTier = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT tier, slot, item_data FROM yw_attendance_reward_items");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String tier = rs.getString("tier");
                byTier.computeIfAbsent(tier, key -> new LinkedHashMap<>())
                        .put(rs.getInt("slot"), ItemStackSerializer.deserialize(rs.getString("item_data")));
            }
        }
        return byTier;
    }

    /** Wipes and re-inserts one tier's items — editing happens rarely (an admin GUI save), so no diffing needed. */
    public void saveItems(String tier, Map<Integer, ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM yw_attendance_reward_items WHERE tier = ?")) {
                    delete.setString(1, tier);
                    delete.executeUpdate();
                }
                if (!items.isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO yw_attendance_reward_items (tier, slot, item_data) VALUES (?, ?, ?)")) {
                        for (var entry : items.entrySet()) {
                            insert.setString(1, tier);
                            insert.setInt(2, entry.getKey());
                            insert.setString(3, ItemStackSerializer.serialize(entry.getValue()));
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
}
