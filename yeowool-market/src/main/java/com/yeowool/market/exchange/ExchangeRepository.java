package com.yeowool.market.exchange;

import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 교환소 entries and per-player usage counts, shared by every server. JDBC only — call from a worker thread. */
public final class ExchangeRepository {

    public enum Limit {
        NONE("없음"), DAILY("일일"), WEEKLY("주간");

        private final String label;

        Limit(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Limit byLabel(String label) {
            for (Limit limit : values()) {
                if (limit.label.equals(label)) {
                    return limit;
                }
            }
            return null;
        }
    }

    /** {@code costItems} may hold several stacks of the same item; {@link ExchangeService} merges them. */
    public record Entry(int id, ItemStack reward, List<ItemStack> costItems, long costMoney, long costStardust,
                        Limit limit, int limitCount) {
    }

    private static final String ENTRIES_DDL = """
            CREATE TABLE IF NOT EXISTS yw_exchange_entries (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                reward MEDIUMTEXT NOT NULL,
                cost_items MEDIUMTEXT NOT NULL,
                cost_money BIGINT NOT NULL DEFAULT 0,
                cost_stardust BIGINT NOT NULL DEFAULT 0,
                limit_type VARCHAR(8) NOT NULL DEFAULT 'NONE',
                limit_count INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String USAGE_DDL = """
            CREATE TABLE IF NOT EXISTS yw_exchange_usage (
                uuid CHAR(36) NOT NULL,
                entry_id INT NOT NULL,
                period VARCHAR(10) NOT NULL,
                count INT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, entry_id, period)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final DataSource dataSource;

    public ExchangeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** The usage bucket a limited entry counts against on {@code date}; null for {@link Limit#NONE}. */
    public static String periodKey(Limit limit, LocalDate date) {
        return switch (limit) {
            case NONE -> null;
            case DAILY -> date.toString();
            case WEEKLY -> date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        };
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(ENTRIES_DDL);
            statement.execute(USAGE_DDL);
        }
    }

    public List<Entry> list() throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, reward, cost_items, cost_money, cost_stardust, limit_type, limit_count FROM yw_exchange_entries ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Limit limit;
                try {
                    limit = Limit.valueOf(rs.getString("limit_type"));
                } catch (IllegalArgumentException e) {
                    limit = Limit.NONE;
                }
                entries.add(new Entry(rs.getInt("id"),
                        ItemStackSerializer.deserialize(rs.getString("reward")),
                        Arrays.stream(ItemStackSerializer.deserializeArray(rs.getString("cost_items"))).filter(i -> i != null && !i.getType().isAir()).toList(),
                        rs.getLong("cost_money"), rs.getLong("cost_stardust"), limit, rs.getInt("limit_count")));
            }
        }
        return entries;
    }

    public int insert(ItemStack reward, List<ItemStack> costItems, long costMoney, long costStardust, Limit limit, int limitCount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_exchange_entries (reward, cost_items, cost_money, cost_stardust, limit_type, limit_count) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ItemStackSerializer.serialize(reward));
            ps.setString(2, ItemStackSerializer.serializeArray(costItems.toArray(new ItemStack[0])));
            ps.setLong(3, costMoney);
            ps.setLong(4, costStardust);
            ps.setString(5, limit.name());
            ps.setInt(6, limitCount);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_exchange_entries WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean exists(int id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM yw_exchange_entries WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** "entryId|period" → times used, for the buckets current on {@code date}. */
    public Map<String, Integer> usage(UUID player, LocalDate date) throws SQLException {
        Map<String, Integer> usage = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT entry_id, period, count FROM yw_exchange_usage WHERE uuid = ? AND period IN (?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, periodKey(Limit.DAILY, date));
            ps.setString(3, periodKey(Limit.WEEKLY, date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usage.put(rs.getInt(1) + "|" + rs.getString(2), rs.getInt(3));
                }
            }
        }
        return usage;
    }

    /** Takes one use from the bucket if it has room; the conditional UPDATE makes concurrent claims (any server) safe. */
    public boolean claimUse(UUID player, int entryId, String period, int limitCount) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_exchange_usage (uuid, entry_id, period, count) VALUES (?, ?, ?, 0)")) {
                ps.setString(1, player.toString());
                ps.setInt(2, entryId);
                ps.setString(3, period);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_exchange_usage SET count = count + 1 WHERE uuid = ? AND entry_id = ? AND period = ? AND count < ?")) {
                ps.setString(1, player.toString());
                ps.setInt(2, entryId);
                ps.setString(3, period);
                ps.setInt(4, limitCount);
                return ps.executeUpdate() == 1;
            }
        }
    }

    public void releaseUse(UUID player, int entryId, String period) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE yw_exchange_usage SET count = count - 1 WHERE uuid = ? AND entry_id = ? AND period = ? AND count > 0")) {
            ps.setString(1, player.toString());
            ps.setInt(2, entryId);
            ps.setString(3, period);
            ps.executeUpdate();
        }
    }
}
