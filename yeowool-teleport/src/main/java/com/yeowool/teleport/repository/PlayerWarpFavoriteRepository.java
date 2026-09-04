package com.yeowool.teleport.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerWarpFavoriteRepository {

    public record FavoriteRow(UUID player, UUID owner, String warpName) {
    }

    private final DataSource dataSource;

    public PlayerWarpFavoriteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<FavoriteRow> loadAll() throws SQLException {
        List<FavoriteRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT player, owner, warp_name FROM yw_player_warp_favorites");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                rows.add(new FavoriteRow(UUID.fromString(rs.getString("player")), UUID.fromString(rs.getString("owner")), rs.getString("warp_name")));
            }
        }
        return rows;
    }

    public void add(UUID player, UUID owner, String warpName, long favoritedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_player_warp_favorites (player, owner, warp_name, favorited_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, player.toString());
            statement.setString(2, owner.toString());
            statement.setString(3, warpName);
            statement.setLong(4, favoritedAt);
            statement.executeUpdate();
        }
    }

    public void remove(UUID player, UUID owner, String warpName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_player_warp_favorites WHERE player = ? AND owner = ? AND warp_name = ?")) {
            delete.setString(1, player.toString());
            delete.setString(2, owner.toString());
            delete.setString(3, warpName);
            delete.executeUpdate();
        }
    }
}
