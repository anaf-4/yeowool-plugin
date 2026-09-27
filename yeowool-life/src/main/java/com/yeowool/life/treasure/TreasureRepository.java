package com.yeowool.life.treasure;

import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Treasure map rows and per-tier reward pools. A map is dug by a single
 * UPDATE that only matches while nobody has dug it and it hasn't expired, so
 * a copied map item or two servers digging at once still pay out once.
 */
public final class TreasureRepository {

    public record LegendaryDig(long id, String diggerName, long dugAt) {
    }

    private static final String MAPS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_treasure_maps (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                finder CHAR(36) NOT NULL,
                tier VARCHAR(16) NOT NULL,
                x INT NOT NULL,
                z INT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                dug_by CHAR(36) NULL,
                dug_by_name VARCHAR(16) NULL,
                dug_at BIGINT NULL,
                INDEX idx_finder_created (finder, created_at),
                INDEX idx_dug_at (dug_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String REWARDS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_treasure_rewards (
                tier VARCHAR(16) NOT NULL,
                slot INT NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                PRIMARY KEY (tier, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public TreasureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(MAPS_DDL);
            statement.executeUpdate(REWARDS_DDL);
        }
    }

    public int countFoundSince(UUID finder, long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_treasure_maps WHERE finder = ? AND created_at >= ?")) {
            ps.setString(1, finder.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public long insertMap(UUID finder, TreasureTier tier, int x, int z, long now, long expiresAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_treasure_maps (finder, tier, x, z, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, finder.toString());
            ps.setString(2, tier.name());
            ps.setInt(3, x);
            ps.setInt(4, z);
            ps.setLong(5, now);
            ps.setLong(6, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("보물지도 id를 받지 못했습니다");
                }
                return keys.getLong(1);
            }
        }
    }

    /** True only for the one caller that marks this unexpired, undug map as dug. */
    public boolean claim(long id, UUID digger, String diggerName, long now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_treasure_maps SET dug_by = ?, dug_by_name = ?, dug_at = ? "
                             + "WHERE id = ? AND dug_by IS NULL AND expires_at > ?")) {
            ps.setString(1, digger.toString());
            ps.setString(2, diggerName);
            ps.setLong(3, now);
            ps.setLong(4, id);
            ps.setLong(5, now);
            return ps.executeUpdate() == 1;
        }
    }

    public List<LegendaryDig> legendaryDugSince(long since) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, dug_by_name, dug_at FROM yw_treasure_maps "
                             + "WHERE tier = 'LEGENDARY' AND dug_at > ? ORDER BY dug_at")) {
            ps.setLong(1, since);
            List<LegendaryDig> digs = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    digs.add(new LegendaryDig(rs.getLong(1), rs.getString(2), rs.getLong(3)));
                }
            }
            return digs;
        }
    }

    public List<ItemStack> loadRewards(TreasureTier tier) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT item_data FROM yw_treasure_rewards WHERE tier = ? ORDER BY slot")) {
            ps.setString(1, tier.name());
            List<ItemStack> items = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(ItemStackSerializer.deserialize(rs.getString(1)));
                }
            }
            return items;
        }
    }

    /** Replaces the tier's whole pool in one transaction. */
    public void saveRewards(TreasureTier tier, List<ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_treasure_rewards WHERE tier = ?")) {
                    ps.setString(1, tier.name());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO yw_treasure_rewards (tier, slot, item_data) VALUES (?, ?, ?)")) {
                    for (int slot = 0; slot < items.size(); slot++) {
                        ps.setString(1, tier.name());
                        ps.setInt(2, slot);
                        ps.setString(3, ItemStackSerializer.serialize(items.get(slot)));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // the pool resets it on return anyway
                }
            }
        }
    }
}
