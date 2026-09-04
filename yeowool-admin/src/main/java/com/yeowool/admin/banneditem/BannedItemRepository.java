package com.yeowool.admin.banneditem;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class BannedItemRepository {

    public record Entry(String material, BanType type, String bannedBy, long bannedAt) {
    }

    private final DataSource dataSource;

    public BannedItemRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Entry> loadAll() throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT material, ban_type, banned_by, banned_at FROM yw_banned_items");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                entries.add(new Entry(rs.getString("material"), BanType.valueOf(rs.getString("ban_type")),
                        rs.getString("banned_by"), rs.getLong("banned_at")));
            }
        }
        return entries;
    }

    public void insert(String material, BanType type, String bannedBy, long bannedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO yw_banned_items (material, ban_type, banned_by, banned_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, material);
            statement.setString(2, type.name());
            statement.setString(3, bannedBy);
            statement.setLong(4, bannedAt);
            statement.executeUpdate();
        }
    }

    public void delete(String material, BanType type) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM yw_banned_items WHERE material = ? AND ban_type = ?")) {
            statement.setString(1, material);
            statement.setString(2, type.name());
            statement.executeUpdate();
        }
    }
}
