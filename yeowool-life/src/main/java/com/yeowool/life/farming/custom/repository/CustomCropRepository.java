package com.yeowool.life.farming.custom.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CustomCropRepository {

    public record CropRow(String world, int x, int y, int z, String cropId, int currentStage, long finishAt) {
    }

    private final DataSource dataSource;

    public CustomCropRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsert(String world, int x, int y, int z, String cropId, int currentStage, long finishAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO yw_custom_farm_crops (world, x, y, z, crop_id, current_stage, finish_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE crop_id = VALUES(crop_id), current_stage = VALUES(current_stage), finish_at = VALUES(finish_at)")) {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            statement.setString(5, cropId);
            statement.setInt(6, currentStage);
            statement.setLong(7, finishAt);
            statement.executeUpdate();
        }
    }

    public void delete(String world, int x, int y, int z) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM yw_custom_farm_crops WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            statement.executeUpdate();
        }
    }

    public List<CropRow> loadAll() throws SQLException {
        List<CropRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT world, x, y, z, crop_id, current_stage, finish_at FROM yw_custom_farm_crops");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new CropRow(
                        rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                        rs.getString("crop_id"), rs.getInt("current_stage"), rs.getLong("finish_at")
                ));
            }
        }
        return rows;
    }
}
