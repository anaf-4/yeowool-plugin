package com.yeowool.teleport.repository;

import com.yeowool.teleport.model.Warp;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_warps}. Must only be called off the main thread. */
public final class WarpRepository {

    private final DataSource dataSource;

    public WarpRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Warp> loadAll() throws SQLException {
        List<Warp> warps = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT name, world, x, y, z, yaw, pitch, created_by, created_at FROM yw_warps");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String createdByStr = rs.getString("created_by");
                warps.add(new Warp(rs.getString("name"), rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                        rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                        createdByStr == null ? null : UUID.fromString(createdByStr), rs.getLong("created_at")));
            }
        }
        return warps;
    }

    public void upsert(Warp warp) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_warps (name, world, x, y, z, yaw, pitch, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
                             + "yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
            upsert.setString(1, warp.name());
            upsert.setString(2, warp.world());
            upsert.setDouble(3, warp.x());
            upsert.setDouble(4, warp.y());
            upsert.setDouble(5, warp.z());
            upsert.setFloat(6, warp.yaw());
            upsert.setFloat(7, warp.pitch());
            upsert.setString(8, warp.createdBy() == null ? null : warp.createdBy().toString());
            upsert.setLong(9, warp.createdAt());
            upsert.executeUpdate();
        }
    }

    public void delete(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_warps WHERE name = ?")) {
            delete.setString(1, name);
            delete.executeUpdate();
        }
    }
}
