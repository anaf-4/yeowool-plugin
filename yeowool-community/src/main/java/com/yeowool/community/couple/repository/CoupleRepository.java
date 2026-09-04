package com.yeowool.community.couple.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_couples}. Like {@code yw_friendships},
 * every pairing is stored as two rows (a->b and b->a) for an O(1) lookup by
 * either side — but unlike friendships, a player can only ever appear as
 * {@code uuid_a} in at most one row, since couples are exclusive 1:1.
 */
public final class CoupleRepository {

    public record Entry(UUID uuid, UUID partner, long since) {
    }

    private final DataSource dataSource;

    public CoupleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Entry> loadAll() throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT uuid_a, uuid_b, since FROM yw_couples");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                entries.add(new Entry(UUID.fromString(rs.getString("uuid_a")), UUID.fromString(rs.getString("uuid_b")), rs.getLong("since")));
            }
        }
        return entries;
    }

    public void insert(UUID a, UUID b, long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_couples (uuid_a, uuid_b, since) VALUES (?, ?, ?)")) {
            insert.setString(1, a.toString());
            insert.setString(2, b.toString());
            insert.setLong(3, since);
            insert.executeUpdate();

            insert.setString(1, b.toString());
            insert.setString(2, a.toString());
            insert.setLong(3, since);
            insert.executeUpdate();
        }
    }

    public void delete(UUID a, UUID b) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_couples WHERE (uuid_a = ? AND uuid_b = ?) OR (uuid_a = ? AND uuid_b = ?)")) {
            delete.setString(1, a.toString());
            delete.setString(2, b.toString());
            delete.setString(3, b.toString());
            delete.setString(4, a.toString());
            delete.executeUpdate();
        }
    }
}
