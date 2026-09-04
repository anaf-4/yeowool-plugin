package com.yeowool.teleport.repository;

import com.yeowool.teleport.model.PlayerWarp;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_player_warps}. */
public final class PlayerWarpRepository {

    private final DataSource dataSource;

    public PlayerWarpRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<PlayerWarp> loadAll() throws SQLException {
        List<PlayerWarp> warps = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT owner, name, world, x, y, z, yaw, pitch, created_at, display_name, description, category, status, preview_item_id, price, visits FROM yw_player_warps");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                warps.add(map(rs));
            }
        }
        return warps;
    }

    private PlayerWarp map(ResultSet rs) throws SQLException {
        return new PlayerWarp(
                UUID.fromString(rs.getString("owner")), rs.getString("name"), rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                rs.getLong("created_at"), rs.getString("display_name"), rs.getString("description"),
                rs.getString("category"), PlayerWarp.Status.valueOf(rs.getString("status")),
                rs.getString("preview_item_id"), rs.getLong("price"), rs.getLong("visits"));
    }

    public void upsert(PlayerWarp warp) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement("""
                     INSERT INTO yw_player_warps (owner, name, world, x, y, z, yaw, pitch, created_at, display_name, description, category, status, preview_item_id, price, visits)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch),
                     display_name = VALUES(display_name), description = VALUES(description), category = VALUES(category), status = VALUES(status),
                     preview_item_id = VALUES(preview_item_id), price = VALUES(price), visits = VALUES(visits)
                     """)) {
            upsert.setString(1, warp.owner().toString());
            upsert.setString(2, warp.name());
            upsert.setString(3, warp.world());
            upsert.setDouble(4, warp.x());
            upsert.setDouble(5, warp.y());
            upsert.setDouble(6, warp.z());
            upsert.setFloat(7, warp.yaw());
            upsert.setFloat(8, warp.pitch());
            upsert.setLong(9, warp.createdAt());
            upsert.setString(10, warp.displayName());
            upsert.setString(11, warp.description());
            upsert.setString(12, warp.category());
            upsert.setString(13, warp.status().name());
            upsert.setString(14, warp.previewItemId());
            upsert.setLong(15, warp.price());
            upsert.setLong(16, warp.visits());
            upsert.executeUpdate();
        }
    }

    /** Renaming or transferring ownership changes part of the primary key — update the row's key columns directly instead of upsert (which would leave the old row behind). */
    public void rekey(UUID oldOwner, String oldName, PlayerWarp updated) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_player_warps SET owner = ?, name = ? WHERE owner = ? AND name = ?")) {
            update.setString(1, updated.owner().toString());
            update.setString(2, updated.name());
            update.setString(3, oldOwner.toString());
            update.setString(4, oldName);
            update.executeUpdate();
        }
    }

    public void delete(UUID owner, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_player_warps WHERE owner = ? AND name = ?")) {
            delete.setString(1, owner.toString());
            delete.setString(2, name);
            delete.executeUpdate();
        }
    }
}
