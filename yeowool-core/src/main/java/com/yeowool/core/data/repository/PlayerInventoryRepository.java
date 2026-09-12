package com.yeowool.core.data.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_player_inventory} — the cross-server
 * inventory/XP/health/hunger sync snapshot ({@link com.yeowool.core.listener.PlayerInventorySyncListener}).
 * Every method here does real I/O and must only be called from
 * {@link com.yeowool.core.database.DatabaseManager#getExecutor()}, never the
 * main server thread (matching {@link PlayerRepository}'s convention).
 */
public final class PlayerInventoryRepository {

    /**
     * A full player-state snapshot — each inventory field is a Base64-encoded
     * {@code ItemStack[]}/{@code ItemStack} blob (see {@code com.yeowool.core.util.ItemStackSerializer}).
     */
    public record Snapshot(String mainInventory, String armor, String offHand, String enderChest,
                            int expLevel, float expProgress, double health, int foodLevel, float saturation) {
    }

    private final DataSource dataSource;

    public PlayerInventoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Snapshot> load(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT main_inventory, armor, off_hand, ender_chest, exp_level, exp_progress, health, "
                             + "food_level, saturation FROM yw_player_inventory WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Snapshot(
                        rs.getString("main_inventory"),
                        rs.getString("armor"),
                        rs.getString("off_hand"),
                        rs.getString("ender_chest"),
                        rs.getInt("exp_level"),
                        rs.getFloat("exp_progress"),
                        rs.getDouble("health"),
                        rs.getInt("food_level"),
                        rs.getFloat("saturation")));
            }
        }
    }

    public void save(UUID uuid, Snapshot snapshot) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_player_inventory (uuid, main_inventory, armor, off_hand, ender_chest, "
                             + "exp_level, exp_progress, health, food_level, saturation, updated_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE main_inventory = VALUES(main_inventory), armor = VALUES(armor), "
                             + "off_hand = VALUES(off_hand), ender_chest = VALUES(ender_chest), "
                             + "exp_level = VALUES(exp_level), exp_progress = VALUES(exp_progress), "
                             + "health = VALUES(health), food_level = VALUES(food_level), saturation = VALUES(saturation), "
                             + "updated_at = VALUES(updated_at)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, snapshot.mainInventory());
            upsert.setString(3, snapshot.armor());
            upsert.setString(4, snapshot.offHand());
            upsert.setString(5, snapshot.enderChest());
            upsert.setInt(6, snapshot.expLevel());
            upsert.setFloat(7, snapshot.expProgress());
            upsert.setDouble(8, snapshot.health());
            upsert.setInt(9, snapshot.foodLevel());
            upsert.setFloat(10, snapshot.saturation());
            upsert.setLong(11, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }
}
