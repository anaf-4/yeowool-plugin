package com.yeowool.teleport.repository;

import com.yeowool.teleport.model.Home;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_homes}. Must only be called off the main thread. */
public final class HomeRepository {

    private final DataSource dataSource;

    public HomeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Home> loadHomes(UUID uuid) throws SQLException {
        List<Home> homes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT name, world, x, y, z, yaw, pitch FROM yw_homes WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    homes.add(new Home(rs.getString("name"), rs.getString("world"), rs.getDouble("x"),
                            rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
                }
            }
        }
        return homes;
    }

    public void upsert(UUID uuid, Home home) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_homes (uuid, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
                             + "yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
            upsert.setString(1, uuid.toString());
            upsert.setString(2, home.name());
            upsert.setString(3, home.world());
            upsert.setDouble(4, home.x());
            upsert.setDouble(5, home.y());
            upsert.setDouble(6, home.z());
            upsert.setFloat(7, home.yaw());
            upsert.setFloat(8, home.pitch());
            upsert.executeUpdate();
        }
    }

    public void delete(UUID uuid, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_homes WHERE uuid = ? AND name = ?")) {
            delete.setString(1, uuid.toString());
            delete.setString(2, name);
            delete.executeUpdate();
        }
    }
}
