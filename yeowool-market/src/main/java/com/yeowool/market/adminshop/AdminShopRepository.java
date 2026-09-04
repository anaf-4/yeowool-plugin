package com.yeowool.market.adminshop;

import com.yeowool.core.util.ItemStackSerializer;
import com.yeowool.market.npcshop.ShopMode;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdminShopRepository {

    public record ShopRow(String id, String title, ShopMode mode, int pageCount, List<Integer> rotationSlots, int rotationIntervalMinutes) {
    }

    private final DataSource dataSource;

    public AdminShopRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, ShopRow> loadShops() throws SQLException {
        Map<String, ShopRow> rows = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, title, mode, page_count, rotation_slots, rotation_interval_minutes FROM yw_admin_shops");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                List<Integer> rotationSlots = parseSlots(rs.getString("rotation_slots"));
                rows.put(id, new ShopRow(id, rs.getString("title"), ShopMode.valueOf(rs.getString("mode")), rs.getInt("page_count"),
                        rotationSlots, rs.getInt("rotation_interval_minutes")));
            }
        }
        return rows;
    }

    private List<Integer> parseSlots(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Integer> slots = new ArrayList<>();
        for (String part : csv.split(",")) {
            try {
                slots.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed entries rather than failing the whole load
            }
        }
        return slots;
    }

    public void updateRotation(String id, List<Integer> slots, int intervalMinutes) throws SQLException {
        String csv = slots.stream().map(String::valueOf).collect(Collectors.joining(","));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_admin_shops SET rotation_slots = ?, rotation_interval_minutes = ? WHERE id = ?")) {
            update.setString(1, csv);
            update.setInt(2, intervalMinutes);
            update.setString(3, id);
            update.executeUpdate();
        }
    }

    /** shopId -> page -> slot -> item. */
    public Map<String, Map<Integer, Map<Integer, ItemStack>>> loadItems() throws SQLException {
        Map<String, Map<Integer, Map<Integer, ItemStack>>> byShop = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT shop_id, page, slot, item_data FROM yw_admin_shop_items");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String shopId = rs.getString("shop_id");
                int page = rs.getInt("page");
                byShop.computeIfAbsent(shopId, key -> new HashMap<>())
                        .computeIfAbsent(page, key -> new LinkedHashMap<>())
                        .put(rs.getInt("slot"), ItemStackSerializer.deserialize(rs.getString("item_data")));
            }
        }
        return byShop;
    }

    public void insertShop(String id, String title, ShopMode mode) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_admin_shops (id, title, mode, page_count) VALUES (?, ?, ?, 1)")) {
            insert.setString(1, id);
            insert.setString(2, title);
            insert.setString(3, mode.name());
            insert.executeUpdate();
        }
    }

    public void deleteShop(String id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_admin_shops WHERE id = ?")) {
            delete.setString(1, id);
            delete.executeUpdate();
        }
    }

    public void updatePageCount(String id, int pageCount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("UPDATE yw_admin_shops SET page_count = ? WHERE id = ?")) {
            update.setInt(1, pageCount);
            update.setString(2, id);
            update.executeUpdate();
        }
    }

    /** Wipes and re-inserts one shop page's items — editing happens rarely (an admin GUI save), so no diffing needed. */
    public void saveItems(String shopId, int page, Map<Integer, ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM yw_admin_shop_items WHERE shop_id = ? AND page = ?")) {
                    delete.setString(1, shopId);
                    delete.setInt(2, page);
                    delete.executeUpdate();
                }
                if (!items.isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO yw_admin_shop_items (shop_id, page, slot, item_data) VALUES (?, ?, ?, ?)")) {
                        for (var entry : items.entrySet()) {
                            insert.setString(1, shopId);
                            insert.setInt(2, page);
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

    public void deletePage(String shopId, int page) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_admin_shop_items WHERE shop_id = ? AND page = ?")) {
            delete.setString(1, shopId);
            delete.setInt(2, page);
            delete.executeUpdate();
        }
    }
}
