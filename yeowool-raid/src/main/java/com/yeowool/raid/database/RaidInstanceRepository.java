package com.yeowool.raid.database;

import com.yeowool.raid.RaidInstanceSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RaidInstanceRepository {

    private final DataSource dataSource;

    public RaidInstanceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Long, List<RaidInstanceSlot>> loadAll() throws SQLException {
        Map<Long, List<RaidInstanceSlot>> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM yw_raid_instance ORDER BY raid_id, slot_index")) {
            while (rs.next()) {
                String worldName = rs.getString("world");
                var world = Bukkit.getWorld(worldName);
                if (world == null) {
                    continue; // world not loaded yet on this boot; skipped, re-read on next reload
                }
                long raidId = rs.getLong("raid_id");
                RaidInstanceSlot slot = new RaidInstanceSlot(
                        rs.getLong("id"),
                        raidId,
                        rs.getInt("slot_index"),
                        new Location(world, rs.getDouble("entry_x"), rs.getDouble("entry_y"), rs.getDouble("entry_z"), rs.getFloat("entry_yaw"), rs.getFloat("entry_pitch")),
                        new Location(world, rs.getDouble("boss_spawn_x"), rs.getDouble("boss_spawn_y"), rs.getDouble("boss_spawn_z")),
                        new Location(world, rs.getDouble("exit_x"), rs.getDouble("exit_y"), rs.getDouble("exit_z"), rs.getFloat("exit_yaw"), rs.getFloat("exit_pitch")),
                        new Location(world, rs.getDouble("bound_min_x"), rs.getDouble("bound_min_y"), rs.getDouble("bound_min_z")),
                        new Location(world, rs.getDouble("bound_max_x"), rs.getDouble("bound_max_y"), rs.getDouble("bound_max_z"))
                );
                result.computeIfAbsent(raidId, k -> new ArrayList<>()).add(slot);
            }
        }
        return result;
    }

    /** field is one of "entry", "boss_spawn", "exit", "bound_min", "bound_max". */
    public void upsertSlot(long raidId, int slotIndex, String field, Location location) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean exists;
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM yw_raid_instance WHERE raid_id = ? AND slot_index = ?")) {
                check.setLong(1, raidId);
                check.setInt(2, slotIndex);
                try (ResultSet rs = check.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO yw_raid_instance (raid_id, slot_index, world) VALUES (?, ?, ?)")) {
                    insert.setLong(1, raidId);
                    insert.setInt(2, slotIndex);
                    insert.setString(3, location.getWorld().getName());
                    insert.executeUpdate();
                }
            }
            boolean hasYawPitch = field.equals("entry") || field.equals("exit");
            String sql = hasYawPitch
                    ? "UPDATE yw_raid_instance SET world = ?, " + field + "_x = ?, " + field + "_y = ?, " + field + "_z = ?, " + field + "_yaw = ?, " + field + "_pitch = ? WHERE raid_id = ? AND slot_index = ?"
                    : "UPDATE yw_raid_instance SET world = ?, " + field + "_x = ?, " + field + "_y = ?, " + field + "_z = ? WHERE raid_id = ? AND slot_index = ?";
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                int i = 1;
                update.setString(i++, location.getWorld().getName());
                update.setDouble(i++, location.getX());
                update.setDouble(i++, location.getY());
                update.setDouble(i++, location.getZ());
                if (hasYawPitch) {
                    update.setFloat(i++, location.getYaw());
                    update.setFloat(i++, location.getPitch());
                }
                update.setLong(i++, raidId);
                update.setInt(i, slotIndex);
                update.executeUpdate();
            }
        }
    }
}
