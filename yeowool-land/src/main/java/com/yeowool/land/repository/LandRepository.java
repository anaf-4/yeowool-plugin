package com.yeowool.land.repository;

import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import com.yeowool.land.model.LandPermission;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_lands} / {@code yw_land_chunks} /
 * {@code yw_land_members}. Must only be called off the main thread (see
 * {@link com.yeowool.land.LandManager} for how results get back onto it).
 */
public final class LandRepository {

    private final DataSource dataSource;

    public LandRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Land> loadAll() throws SQLException {
        Map<UUID, Land> lands = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, owner_uuid, name, pvp_enabled, bank_balance FROM yw_lands");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    Land land = new Land(id, UUID.fromString(rs.getString("owner_uuid")));
                    land.setName(rs.getString("name"));
                    land.setPvpEnabled(rs.getBoolean("pvp_enabled"));
                    land.setBankBalance(rs.getLong("bank_balance"));
                    lands.put(id, land);
                }
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT land_id, world, chunk_x, chunk_z FROM yw_land_chunks");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    Land land = lands.get(UUID.fromString(rs.getString("land_id")));
                    if (land != null) {
                        land.addChunk(new ChunkKey(rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z")));
                    }
                }
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT land_id, member_uuid, can_build, can_containers FROM yw_land_members");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    Land land = lands.get(UUID.fromString(rs.getString("land_id")));
                    if (land != null) {
                        Set<LandPermission> permissions = EnumSet.noneOf(LandPermission.class);
                        if (rs.getBoolean("can_build")) {
                            permissions.add(LandPermission.BUILD);
                        }
                        if (rs.getBoolean("can_containers")) {
                            permissions.add(LandPermission.CONTAINERS);
                        }
                        land.addMember(UUID.fromString(rs.getString("member_uuid")), permissions);
                    }
                }
            }
        }
        return new ArrayList<>(lands.values());
    }

    public void insertLand(Land land) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_lands (id, owner_uuid, pvp_enabled, created_at) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, land.getId().toString());
            insert.setString(2, land.getOwner().toString());
            insert.setBoolean(3, land.isPvpEnabled());
            insert.setLong(4, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }

    public void insertChunk(UUID landId, ChunkKey key) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_land_chunks (land_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, landId.toString());
            insert.setString(2, key.world());
            insert.setInt(3, key.x());
            insert.setInt(4, key.z());
            insert.executeUpdate();
        }
    }

    public void insertMember(UUID landId, UUID memberUuid, Set<LandPermission> permissions) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_land_members (land_id, member_uuid, can_build, can_containers) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE can_build = VALUES(can_build), can_containers = VALUES(can_containers)")) {
            insert.setString(1, landId.toString());
            insert.setString(2, memberUuid.toString());
            insert.setBoolean(3, permissions.contains(LandPermission.BUILD));
            insert.setBoolean(4, permissions.contains(LandPermission.CONTAINERS));
            insert.executeUpdate();
        }
    }

    public void deleteMember(UUID landId, UUID memberUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_land_members WHERE land_id = ? AND member_uuid = ?")) {
            delete.setString(1, landId.toString());
            delete.setString(2, memberUuid.toString());
            delete.executeUpdate();
        }
    }

    public void updateOwner(UUID landId, UUID newOwner) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_lands SET owner_uuid = ? WHERE id = ?")) {
            update.setString(1, newOwner.toString());
            update.setString(2, landId.toString());
            update.executeUpdate();
        }
    }

    public void updateName(UUID landId, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_lands SET name = ? WHERE id = ?")) {
            update.setString(1, name);
            update.setString(2, landId.toString());
            update.executeUpdate();
        }
    }

    public void updatePvp(UUID landId, boolean pvpEnabled) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_lands SET pvp_enabled = ? WHERE id = ?")) {
            update.setBoolean(1, pvpEnabled);
            update.setString(2, landId.toString());
            update.executeUpdate();
        }
    }

    public void updateBankBalance(UUID landId, long balance) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_lands SET bank_balance = ? WHERE id = ?")) {
            update.setLong(1, balance);
            update.setString(2, landId.toString());
            update.executeUpdate();
        }
    }

    /** Cascades to {@code yw_land_chunks} / {@code yw_land_members} via foreign keys. */
    public void deleteLand(UUID landId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_lands WHERE id = ?")) {
            delete.setString(1, landId.toString());
            delete.executeUpdate();
        }
    }

    public record BarrelLocation(String world, int x, int y, int z) {
    }

    public void insertBarrel(BarrelLocation location) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_land_barrels (world, x, y, z) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, location.world());
            insert.setInt(2, location.x());
            insert.setInt(3, location.y());
            insert.setInt(4, location.z());
            insert.executeUpdate();
        }
    }

    public List<BarrelLocation> loadAllBarrels() throws SQLException {
        List<BarrelLocation> locations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT world, x, y, z FROM yw_land_barrels");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                locations.add(new BarrelLocation(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
            }
        }
        return locations;
    }
}
