package com.yeowool.community.ranking.statue;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Blocking JDBC access to {@code yw_ranking_statues}. Must only be called off the main thread. */
public final class RankingStatueRepository {

    /** One admin-registered statue slot: where the NPC stands, and which Citizens NPC id it is (once created). */
    public record StatueLocation(String world, double x, double y, double z, float yaw, float pitch, Integer npcId) {
    }

    private final DataSource dataSource;

    public RankingStatueRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Keyed by {@code "<category>:<position>"}. */
    public Map<String, StatueLocation> loadAll() throws SQLException {
        Map<String, StatueLocation> locations = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT category, position, world, x, y, z, yaw, pitch, npc_id FROM yw_ranking_statues");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("category") + ":" + rs.getInt("position");
                int npcId = rs.getInt("npc_id");
                locations.put(key, new StatueLocation(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                        rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"), rs.wasNull() ? null : npcId));
            }
        }
        return locations;
    }

    public void upsert(String category, int position, StatueLocation location) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_ranking_statues (category, position, world, x, y, z, yaw, pitch, npc_id) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                             + "world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), "
                             + "yaw = VALUES(yaw), pitch = VALUES(pitch), npc_id = VALUES(npc_id)")) {
            upsert.setString(1, category);
            upsert.setInt(2, position);
            upsert.setString(3, location.world());
            upsert.setDouble(4, location.x());
            upsert.setDouble(5, location.y());
            upsert.setDouble(6, location.z());
            upsert.setFloat(7, location.yaw());
            upsert.setFloat(8, location.pitch());
            if (location.npcId() != null) {
                upsert.setInt(9, location.npcId());
            } else {
                upsert.setNull(9, java.sql.Types.INTEGER);
            }
            upsert.executeUpdate();
        }
    }
}
