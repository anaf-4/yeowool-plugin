package com.yeowool.market.casino;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/** Chip balances, round log, daily buy totals and big-win announcements, shared by every server. JDBC only — worker thread. */
public final class CasinoRepository {

    public record LogRow(String game, long bet, long payout, String detail, long createdAt) {
    }

    public record Announcement(long id, String player, String game, long bet, long payout) {
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final List<String> DDL = List.of("""
            CREATE TABLE IF NOT EXISTS yw_casino_chips (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                chips BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_casino_log (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                uuid CHAR(36) NOT NULL,
                game VARCHAR(16) NOT NULL,
                bet BIGINT NOT NULL,
                payout BIGINT NOT NULL,
                detail VARCHAR(255) NOT NULL,
                created_at BIGINT NOT NULL,
                KEY idx_uuid (uuid, id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_casino_daily (
                uuid CHAR(36) NOT NULL,
                day VARCHAR(10) NOT NULL,
                bought BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, day)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_casino_announcements (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                player VARCHAR(32) NOT NULL,
                game VARCHAR(16) NOT NULL,
                bet BIGINT NOT NULL,
                payout BIGINT NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

    private final DataSource dataSource;

    public CasinoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
        }
    }

    public long balance(UUID player) throws SQLException {
        return single("SELECT chips FROM yw_casino_chips WHERE uuid = ?", player.toString());
    }

    public long boughtToday(UUID player, String day) throws SQLException {
        return single("SELECT bought FROM yw_casino_daily WHERE uuid = ? AND day = ?", player.toString(), day);
    }

    /**
     * Adds {@code chips} only if today's total stays within {@code dailyLimit} (all servers share the row).
     * Returns false, changing nothing, when the limit would be passed.
     */
    public boolean buy(UUID player, long chips, long dailyLimit, String day) throws SQLException {
        return transaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    // exclusive row lock up front (INSERT IGNORE takes a shared one → two buys could deadlock)
                    "INSERT INTO yw_casino_daily (uuid, day, bought) VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE bought = bought")) {
                ps.setString(1, player.toString());
                ps.setString(2, day);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_casino_daily SET bought = bought + ? WHERE uuid = ? AND day = ? AND bought + ? <= ?")) {
                ps.setLong(1, chips);
                ps.setString(2, player.toString());
                ps.setString(3, day);
                ps.setLong(4, chips);
                ps.setLong(5, dailyLimit);
                if (ps.executeUpdate() != 1) {
                    return false;
                }
            }
            return change(connection, player, 0, chips) >= 0;
        });
    }

    /**
     * One atomic step: takes {@code take} chips (only if the player has them) and gives {@code give} in the same
     * UPDATE, and — when {@code game} is set — logs the finished round in the same transaction.
     * Returns the new balance, or empty (nothing changed) if the player lacked {@code take} chips.
     */
    public OptionalLong settle(UUID player, long take, long give, String game, long bet, String detail) throws SQLException {
        return transaction(connection -> {
            long balance = change(connection, player, take, give);
            if (balance < 0) {
                return OptionalLong.empty();
            }
            if (game != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO yw_casino_log (uuid, game, bet, payout, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, game);
                    ps.setLong(3, bet);
                    ps.setLong(4, give);
                    ps.setString(5, detail.length() > 255 ? detail.substring(0, 255) : detail);
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
            return OptionalLong.of(balance);
        });
    }

    public OptionalLong take(UUID player, long chips) throws SQLException {
        return settle(player, chips, 0, null, 0, "");
    }

    public long give(UUID player, long chips) throws SQLException {
        return settle(player, 0, chips, null, 0, "").orElseThrow();
    }

    /** Inside a transaction: the new balance, or -1 (and the caller must roll back) if {@code take} wasn't covered. */
    private static long change(Connection connection, UUID player, long take, long give) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO yw_casino_chips (uuid, chips) VALUES (?, 0) ON DUPLICATE KEY UPDATE chips = chips")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE yw_casino_chips SET chips = chips - ? + ? WHERE uuid = ? AND chips >= ?")) {
            ps.setLong(1, take);
            ps.setLong(2, give);
            ps.setString(3, player.toString());
            ps.setLong(4, take);
            if (ps.executeUpdate() != 1) {
                return -1;
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT chips FROM yw_casino_chips WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public List<LogRow> recent(UUID player, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT game, bet, payout, detail, created_at FROM yw_casino_log WHERE uuid = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, player.toString());
            ps.setInt(2, limit);
            List<LogRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LogRow(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getLong(5)));
                }
            }
            return rows;
        }
    }

    // ---- cross-server big-win announcements ----

    public void announce(String player, String game, long bet, long payout) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_casino_announcements (player, game, bet, payout, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, player);
            ps.setString(2, game);
            ps.setLong(3, bet);
            ps.setLong(4, payout);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public long maxAnnouncementId() throws SQLException {
        return single("SELECT COALESCE(MAX(id), 0) FROM yw_casino_announcements");
    }

    // ponytail: announcement rows are never pruned — only big wins land here; add a cleanup if the table ever matters.
    public List<Announcement> announcementsAfter(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, player, game, bet, payout FROM yw_casino_announcements WHERE id > ? ORDER BY id")) {
            ps.setLong(1, id);
            List<Announcement> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Announcement(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)));
                }
            }
            return rows;
        }
    }

    // ---- helpers ----

    private long single(String sql, String... args) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Commits when {@code work} returns a present / true / non-negative result, rolls back otherwise or on error. */
    private <T> T transaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                boolean ok = !(result instanceof OptionalLong o && o.isEmpty()) && !Boolean.FALSE.equals(result);
                if (ok) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return result;
            } catch (SQLException | RuntimeException e) {
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
