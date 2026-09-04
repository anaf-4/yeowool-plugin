package com.yeowool.admin.starterkit;

import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StarterKitRepository {

    private final DataSource dataSource;

    public StarterKitRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Integer, ItemStack> loadAll() throws SQLException {
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT slot, item_data FROM yw_starter_kit_items");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                items.put(rs.getInt("slot"), ItemStackSerializer.deserialize(rs.getString("item_data")));
            }
        }
        return items;
    }

    /** Wipes and re-inserts the whole kit — editing happens rarely (an admin GUI save), so no diffing needed. */
    public void replaceAll(Map<Integer, ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM yw_starter_kit_items");
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO yw_starter_kit_items (slot, item_data) VALUES (?, ?)")) {
                    for (var entry : items.entrySet()) {
                        insert.setInt(1, entry.getKey());
                        insert.setString(2, ItemStackSerializer.serialize(entry.getValue()));
                        insert.addBatch();
                    }
                    if (!items.isEmpty()) {
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
