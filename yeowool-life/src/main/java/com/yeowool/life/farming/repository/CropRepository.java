package com.yeowool.life.farming.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CropRepository {

    public record CropRow(String world, int x, int y, int z, long finishAt) {
    }

    private final DataSource dataSource;

    public CropRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(String world, int x, int y, int z, long finishAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_farm_crops (world, x, y, z, finish_at) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE finish_at = VALUES(finish_at)")) {
            insert.setString(1, world);
            insert.setInt(2, x);
            insert.setInt(3, y);
            insert.setInt(4, z);
            insert.setLong(5, finishAt);
            insert.executeUpdate();
        }
    }

    public void delete(String world, int x, int y, int z) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_farm_crops WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            delete.setString(1, world);
            delete.setInt(2, x);
            delete.setInt(3, y);
            delete.setInt(4, z);
            delete.executeUpdate();
        }
    }

    public List<CropRow> loadAll() throws SQLException {
        List<CropRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT world, x, y, z, finish_at FROM yw_farm_crops");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                rows.add(new CropRow(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getLong("finish_at")));
            }
        }
        return rows;
    }
}
