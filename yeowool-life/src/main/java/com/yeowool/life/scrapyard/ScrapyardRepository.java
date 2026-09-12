package com.yeowool.life.scrapyard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw DB access for 폐기장's admin-registered points/chests/mob spawns — see
 * {@link ScrapyardLocationStore}. Deliberately keeps every location as a
 * plain world-name + coordinates tuple, never a resolved {@link Location}/
 * {@link World} reference: a {@link World} object can become stale (e.g. a
 * world manager plugin re-importing/reloading a world after this plugin
 * already cached one), silently breaking every teleport/comparison that
 * still held the old instance. Resolving {@code Bukkit.getWorld(name)}
 * fresh at the point of use (see each record's {@code toLocation()}) avoids
 * that entirely.
 */
public final class ScrapyardRepository {

    public record NamedPoint(String world, double x, double y, double z, float yaw, float pitch) {
        /** Null if {@code world} isn't currently loaded. */
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    public record Chest(int id, String world, int x, int y, int z, String lastOpenedDate) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    public record MobSpawn(int id, String world, double x, double y, double z) {
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final DataSource dataSource;

    public ScrapyardRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, NamedPoint> loadPoints() throws SQLException {
        Map<String, NamedPoint> points = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT category, world, x, y, z, yaw, pitch FROM yw_scrapyard_point");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                points.put(rs.getString("category"), new NamedPoint(rs.getString("world"), rs.getDouble("x"),
                        rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
            }
        }
        return points;
    }

    public void savePoint(String category, String world, double x, double y, double z, float yaw, float pitch) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_scrapyard_point (category, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
                             + "yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
            upsert.setString(1, category);
            upsert.setString(2, world);
            upsert.setDouble(3, x);
            upsert.setDouble(4, y);
            upsert.setDouble(5, z);
            upsert.setFloat(6, yaw);
            upsert.setFloat(7, pitch);
            upsert.executeUpdate();
        }
    }

    public List<Chest> loadChests() throws SQLException {
        List<Chest> chests = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, world, x, y, z, last_opened_date FROM yw_scrapyard_chest");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                chests.add(new Chest(rs.getInt("id"), rs.getString("world"), rs.getInt("x"), rs.getInt("y"),
                        rs.getInt("z"), rs.getString("last_opened_date")));
            }
        }
        return chests;
    }

    public int addChest(String world, int x, int y, int z) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_scrapyard_chest (world, x, y, z) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, world);
            insert.setInt(2, x);
            insert.setInt(3, y);
            insert.setInt(4, z);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void removeChest(int id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_scrapyard_chest WHERE id = ?")) {
            delete.setInt(1, id);
            delete.executeUpdate();
        }
    }

    public void markChestOpened(int id, String date) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_scrapyard_chest SET last_opened_date = ? WHERE id = ?")) {
            update.setString(1, date);
            update.setInt(2, id);
            update.executeUpdate();
        }
    }

    public List<MobSpawn> loadMobSpawns() throws SQLException {
        List<MobSpawn> spawns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT id, world, x, y, z FROM yw_scrapyard_mob_spawn");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                spawns.add(new MobSpawn(rs.getInt("id"), rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")));
            }
        }
        return spawns;
    }

    public int addMobSpawn(String world, double x, double y, double z) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_scrapyard_mob_spawn (world, x, y, z) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, world);
            insert.setDouble(2, x);
            insert.setDouble(3, y);
            insert.setDouble(4, z);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void removeMobSpawn(int id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_scrapyard_mob_spawn WHERE id = ?")) {
            delete.setInt(1, id);
            delete.executeUpdate();
        }
    }
}
