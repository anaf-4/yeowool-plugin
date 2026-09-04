package com.yeowool.life.bag.repository;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_player_bags}. Must only be called off the main thread. */
public final class BagRepository {

    private final DataSource dataSource;

    public BagRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Row(int capacity, ItemStack[] contents) {
    }

    public Map<String, Row> load(UUID uuid) throws SQLException {
        Map<String, Row> rows = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT bag_type, capacity, contents FROM yw_player_bags WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.put(rs.getString("bag_type"), new Row(rs.getInt("capacity"), deserialize(rs.getString("contents"))));
                }
            }
        }
        return rows;
    }

    public void save(UUID uuid, String bagType, int capacity, ItemStack[] contents) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_player_bags (uuid, bag_type, capacity, contents) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE capacity = VALUES(capacity), contents = VALUES(contents)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, bagType);
            upsert.setInt(3, capacity);
            upsert.setString(4, serialize(contents));
            upsert.executeUpdate();
        }
    }

    private static String serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteStream)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("가방 아이템 직렬화 실패", e);
        }
    }

    private static ItemStack[] deserialize(String data) {
        if (data == null || data.isBlank()) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteStream)) {
            int length = in.readInt();
            ItemStack[] contents = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("가방 아이템 역직렬화 실패", e);
        }
    }
}
