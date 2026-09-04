package com.yeowool.community.friend.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_friendships}. Every friendship is stored
 * as two rows (a->b and b->a) so {@link #friendsOf(UUID)} is a single
 * indexed lookup instead of an OR-across-two-columns query.
 */
public final class FriendRepository {

    private final DataSource dataSource;

    public FriendRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(UUID a, UUID b, long createdAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_friendships (uuid_a, uuid_b, created_at) VALUES (?, ?, ?)")) {
            insert.setString(1, a.toString());
            insert.setString(2, b.toString());
            insert.setLong(3, createdAt);
            insert.executeUpdate();

            insert.setString(1, b.toString());
            insert.setString(2, a.toString());
            insert.setLong(3, createdAt);
            insert.executeUpdate();
        }
    }

    public void delete(UUID a, UUID b) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_friendships WHERE (uuid_a = ? AND uuid_b = ?) OR (uuid_a = ? AND uuid_b = ?)")) {
            delete.setString(1, a.toString());
            delete.setString(2, b.toString());
            delete.setString(3, b.toString());
            delete.setString(4, a.toString());
            delete.executeUpdate();
        }
    }

    public List<UUID> friendsOf(UUID uuid) throws SQLException {
        List<UUID> friends = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT uuid_b FROM yw_friendships WHERE uuid_a = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    friends.add(UUID.fromString(rs.getString("uuid_b")));
                }
            }
        }
        return friends;
    }
}
