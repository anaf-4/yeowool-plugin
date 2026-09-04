package com.yeowool.community.battlepass.repository;

import com.yeowool.community.battlepass.BattlePassTrack;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Blocking JDBC access to the reward-definition tables (see {@link com.yeowool.community.battlepass.database.BattlePassSchemaInitializer}). */
public final class BattlePassRepository {

    public record RewardConfigRow(long requiredPoints, long amount, CurrencyType currency) {
    }

    private final DataSource dataSource;

    public BattlePassRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<BattlePassTrack, Map<Integer, RewardConfigRow>> loadRewardConfig() throws SQLException {
        Map<BattlePassTrack, Map<Integer, RewardConfigRow>> result = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT track, tier, required_points, amount, currency FROM yw_battlepass_reward_config")) {
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    BattlePassTrack track = parseTrack(rs.getString("track"));
                    if (track == null) {
                        continue;
                    }
                    result.computeIfAbsent(track, t -> new HashMap<>()).put(rs.getInt("tier"),
                            new RewardConfigRow(rs.getLong("required_points"), rs.getLong("amount"),
                                    CurrencyType.valueOf(rs.getString("currency"))));
                }
            }
        }
        return result;
    }

    public void saveRewardConfig(BattlePassTrack track, int tier, long requiredPoints, long amount, CurrencyType currency) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_battlepass_reward_config (track, tier, required_points, amount, currency) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE required_points = VALUES(required_points), amount = VALUES(amount), currency = VALUES(currency)")) {
            upsert.setString(1, track.key());
            upsert.setInt(2, tier);
            upsert.setLong(3, requiredPoints);
            upsert.setLong(4, amount);
            upsert.setString(5, currency.name());
            upsert.executeUpdate();
        }
    }

    public Map<BattlePassTrack, Map<Integer, Map<Integer, ItemStack>>> loadRewardItems() throws SQLException {
        Map<BattlePassTrack, Map<Integer, Map<Integer, ItemStack>>> result = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT track, tier, slot, item_data FROM yw_battlepass_reward_items")) {
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    BattlePassTrack track = parseTrack(rs.getString("track"));
                    if (track == null) {
                        continue;
                    }
                    ItemStack item = ItemStackSerializer.deserialize(rs.getString("item_data"));
                    result.computeIfAbsent(track, t -> new HashMap<>())
                            .computeIfAbsent(rs.getInt("tier"), t -> new HashMap<>())
                            .put(rs.getInt("slot"), item);
                }
            }
        }
        return result;
    }

    /**
     * Delete-then-insert wrapped in one transaction: without this, an insert failing partway
     * through (bad serialization, a dropped connection mid-batch) would leave the delete
     * committed on its own, permanently losing every item for this tier in the DB even though
     * the in-memory cache (updated optimistically before this async write runs) still has them —
     * a later {@code loadIntoCache()} on restart would then silently revert the tier to empty.
     */
    public void saveRewardItems(BattlePassTrack track, int tier, Map<Integer, ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM yw_battlepass_reward_items WHERE track = ? AND tier = ?")) {
                    delete.setString(1, track.key());
                    delete.setInt(2, tier);
                    delete.executeUpdate();
                }
                if (!items.isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO yw_battlepass_reward_items (track, tier, slot, item_data) VALUES (?, ?, ?, ?)")) {
                        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                            insert.setString(1, track.key());
                            insert.setInt(2, tier);
                            insert.setInt(3, entry.getKey());
                            insert.setString(4, ItemStackSerializer.serialize(entry.getValue()));
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

    private static BattlePassTrack parseTrack(String raw) {
        for (BattlePassTrack track : BattlePassTrack.values()) {
            if (track.key().equals(raw)) {
                return track;
            }
        }
        return null;
    }
}
