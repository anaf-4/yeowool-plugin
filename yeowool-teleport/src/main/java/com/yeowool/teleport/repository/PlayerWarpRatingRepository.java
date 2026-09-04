package com.yeowool.teleport.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerWarpRatingRepository {

    public record RatingRow(UUID owner, String warpName, UUID rater, int rating) {
    }

    private final DataSource dataSource;

    public PlayerWarpRatingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<RatingRow> loadAll() throws SQLException {
        List<RatingRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT owner, warp_name, rater, rating FROM yw_player_warp_ratings");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                rows.add(new RatingRow(UUID.fromString(rs.getString("owner")), rs.getString("warp_name"), UUID.fromString(rs.getString("rater")), rs.getInt("rating")));
            }
        }
        return rows;
    }

    public void upsert(UUID owner, String warpName, UUID rater, int rating, long ratedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO yw_player_warp_ratings (owner, warp_name, rater, rating, rated_at) VALUES (?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE rating = VALUES(rating), rated_at = VALUES(rated_at)
                     """)) {
            statement.setString(1, owner.toString());
            statement.setString(2, warpName);
            statement.setString(3, rater.toString());
            statement.setInt(4, rating);
            statement.setLong(5, ratedAt);
            statement.executeUpdate();
        }
    }

    public void deleteForWarp(UUID owner, String warpName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_player_warp_ratings WHERE owner = ? AND warp_name = ?")) {
            delete.setString(1, owner.toString());
            delete.setString(2, warpName);
            delete.executeUpdate();
        }
    }
}
