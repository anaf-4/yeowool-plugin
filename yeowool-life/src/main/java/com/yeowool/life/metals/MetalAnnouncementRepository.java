package com.yeowool.life.metals;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Legendary-ore finds, shared by the three servers so each broadcasts them on its next poll. Blocking JDBC: worker thread only. */
final class MetalAnnouncementRepository {

    record Find(long id, String player, String metalId) {
    }

    private final DataSource dataSource;

    MetalAnnouncementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void createTable() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS yw_metal_announcements (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        player VARCHAR(16) NOT NULL,
                        metal_id VARCHAR(32) NOT NULL,
                        created_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    long maxId() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM yw_metal_announcements");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    void insert(String player, String metalId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_metal_announcements (player, metal_id, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, player);
            ps.setString(2, metalId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    // ponytail: rows are never pruned — legendary finds are ~1% of 0.4–2% of blocks; add a cleanup if the table ever matters.
    List<Find> after(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, player, metal_id FROM yw_metal_announcements WHERE id > ? ORDER BY id")) {
            ps.setLong(1, id);
            List<Find> finds = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    finds.add(new Find(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
            return finds;
        }
    }
}
