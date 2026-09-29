package com.yeowool.life.cooking.orders;

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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Shared (all three servers) 요리 주문 rows. Every step that must happen once — delivering past the
 * required amount, completing an order, the all-done bonus, 교체, group progress, starting/finishing
 * and settling a group order — is a conditional UPDATE / unique-key INSERT, so only one caller wins.
 * Blocking JDBC: worker thread only.
 */
public final class CookingOrderRepository {

    public record Order(int slot, String recipeId, String difficulty, boolean vip, int required, int delivered, boolean completed) {
    }

    public record Daily(boolean rerolled, boolean bonusPaid) {
    }

    public record GroupOrder(int id, String recipeId, int target, int progress, long startsAt, long endsAt, String status) {
    }

    public record Contribution(UUID player, String name, int amount) {
    }

    public record Announcement(long id, String key, Map<String, String> args) {
    }

    private static final String ORDERS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_cook_orders (
                uuid CHAR(36) NOT NULL,
                day VARCHAR(10) NOT NULL,
                slot TINYINT NOT NULL,
                recipe_id VARCHAR(64) NOT NULL,
                difficulty VARCHAR(8) NOT NULL,
                vip TINYINT(1) NOT NULL DEFAULT 0,
                required INT NOT NULL,
                delivered INT NOT NULL DEFAULT 0,
                completed TINYINT(1) NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, day, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String DAILY_DDL = """
            CREATE TABLE IF NOT EXISTS yw_cook_daily (
                uuid CHAR(36) NOT NULL,
                day VARCHAR(10) NOT NULL,
                rerolled TINYINT(1) NOT NULL DEFAULT 0,
                bonus_paid TINYINT(1) NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, day)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String FAME_DDL = """
            CREATE TABLE IF NOT EXISTS yw_cook_fame (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                fame INT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    // active_lock is 1 while ACTIVE and NULL afterwards: its unique key allows only one running group order,
    // and the unique starts_at stops the three servers starting the same scheduled one twice.
    private static final String GROUP_DDL = """
            CREATE TABLE IF NOT EXISTS yw_cook_group (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                recipe_id VARCHAR(64) NOT NULL,
                target INT NOT NULL,
                progress INT NOT NULL DEFAULT 0,
                starts_at BIGINT NOT NULL,
                ends_at BIGINT NOT NULL,
                status VARCHAR(10) NOT NULL,
                settled TINYINT(1) NOT NULL DEFAULT 0,
                active_lock TINYINT(1) NULL,
                UNIQUE KEY uk_starts_at (starts_at),
                UNIQUE KEY uk_active (active_lock)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String CONTRIB_DDL = """
            CREATE TABLE IF NOT EXISTS yw_cook_group_contrib (
                group_id INT NOT NULL,
                uuid CHAR(36) NOT NULL,
                name VARCHAR(16) NOT NULL,
                amount INT NOT NULL DEFAULT 0,
                PRIMARY KEY (group_id, uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String ANNOUNCEMENTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_cook_announcements (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                msg_key VARCHAR(64) NOT NULL,
                args VARCHAR(1024) NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String GROUP_COLUMNS = "id, recipe_id, target, progress, starts_at, ends_at, status";

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final DataSource dataSource;

    public CookingOrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : List.of(ORDERS_DDL, DAILY_DDL, FAME_DDL, GROUP_DDL, CONTRIB_DDL, ANNOUNCEMENTS_DDL)) {
                statement.executeUpdate(ddl);
            }
        }
    }

    // ---- personal orders ----

    public List<Order> orders(UUID uuid, String day) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT slot, recipe_id, difficulty, vip, required, delivered, completed FROM yw_cook_orders "
                             + "WHERE uuid = ? AND day = ? ORDER BY slot")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, day);
            List<Order> orders = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new Order(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                            rs.getInt(5), rs.getInt(6), rs.getBoolean(7)));
                }
            }
            return orders;
        }
    }

    /** Stores {@code draws} as the day's orders unless another server already did (the daily row decides who). */
    public void insertOrdersIfAbsent(UUID uuid, String day, List<CookingOrderRules.Draw> draws) throws SQLException {
        inTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT IGNORE INTO yw_cook_daily (uuid, day) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, day);
                if (ps.executeUpdate() == 0) {
                    return null;
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO yw_cook_orders (uuid, day, slot, recipe_id, difficulty, vip, required) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (int slot = 0; slot < draws.size(); slot++) {
                    CookingOrderRules.Draw draw = draws.get(slot);
                    ps.setString(1, uuid.toString());
                    ps.setString(2, day);
                    ps.setInt(3, slot);
                    ps.setString(4, draw.recipeId());
                    ps.setString(5, draw.difficulty().key());
                    ps.setBoolean(6, draw.vip());
                    ps.setInt(7, draw.required());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    public Daily daily(UUID uuid, String day) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT rerolled, bonus_paid FROM yw_cook_daily WHERE uuid = ? AND day = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Daily(rs.getBoolean(1), rs.getBoolean(2)) : new Daily(false, false);
            }
        }
    }

    /**
     * Adds up to {@code amount} to an open order that still has that recipe, never past {@code required}
     * (the row is locked while the remaining amount is read). Returns how many were added — 0 if it's full.
     */
    public int addDelivered(UUID uuid, String day, int slot, String recipeId, int amount) throws SQLException {
        return inTransaction(connection -> {
            int remaining;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT required - delivered FROM yw_cook_orders WHERE uuid = ? AND day = ? AND slot = ? "
                            + "AND recipe_id = ? AND completed = 0 FOR UPDATE")) {
                bind(ps, uuid, day, slot, recipeId);
                try (ResultSet rs = ps.executeQuery()) {
                    remaining = rs.next() ? rs.getInt(1) : 0;
                }
            }
            int added = Math.min(amount, remaining);
            if (added <= 0) {
                return 0;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_cook_orders SET delivered = delivered + ? WHERE uuid = ? AND day = ? AND slot = ?")) {
                bind(ps, added, uuid, day, slot);
                ps.executeUpdate();
            }
            return added;
        });
    }

    public void undoDelivered(UUID uuid, String day, int slot, int amount) throws SQLException {
        update("UPDATE yw_cook_orders SET delivered = delivered - ? WHERE uuid = ? AND day = ? AND slot = ? AND delivered >= ?",
                amount, uuid, day, slot, amount);
    }

    /** True only for the one caller that marks this full order completed. */
    public boolean completeOrder(UUID uuid, String day, int slot) throws SQLException {
        return update("UPDATE yw_cook_orders SET completed = 1 WHERE uuid = ? AND day = ? AND slot = ? "
                + "AND completed = 0 AND delivered >= required", uuid, day, slot) == 1;
    }

    public boolean allCompleted(UUID uuid, String day) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*), COALESCE(SUM(completed), 0) FROM yw_cook_orders WHERE uuid = ? AND day = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0 && rs.getInt(1) == rs.getInt(2);
            }
        }
    }

    public boolean claimBonus(UUID uuid, String day) throws SQLException {
        return update("UPDATE yw_cook_daily SET bonus_paid = 1 WHERE uuid = ? AND day = ? AND bonus_paid = 0", uuid, day) == 1;
    }

    public boolean claimReroll(UUID uuid, String day) throws SQLException {
        return update("UPDATE yw_cook_daily SET rerolled = 1 WHERE uuid = ? AND day = ? AND rerolled = 0", uuid, day) == 1;
    }

    public void releaseReroll(UUID uuid, String day) throws SQLException {
        update("UPDATE yw_cook_daily SET rerolled = 0 WHERE uuid = ? AND day = ?", uuid, day);
    }

    /**
     * Swaps an open order for {@code draw}. A paid 교체 also needs it untouched and non-VIP; a free one
     * (the recipe no longer exists) only needs it open.
     */
    public boolean replaceOrder(UUID uuid, String day, int slot, String oldRecipeId, CookingOrderRules.Draw draw, boolean free)
            throws SQLException {
        return update("UPDATE yw_cook_orders SET recipe_id = ?, difficulty = ?, vip = ?, required = ?, delivered = 0, completed = 0 "
                        + "WHERE uuid = ? AND day = ? AND slot = ? AND recipe_id = ? AND completed = 0"
                        + (free ? "" : " AND delivered = 0 AND vip = 0"),
                draw.recipeId(), draw.difficulty().key(), draw.vip(), draw.required(), uuid, day, slot, oldRecipeId) == 1;
    }

    /** Admin 초기화: the next open draws the day's orders again (also resets 교체 and the all-done bonus). */
    public void resetDay(UUID uuid, String day) throws SQLException {
        inTransaction(connection -> {
            for (String sql : List.of("DELETE FROM yw_cook_orders WHERE uuid = ? AND day = ?", "DELETE FROM yw_cook_daily WHERE uuid = ? AND day = ?")) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, day);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    // ---- fame ----

    public int fame(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return fame(connection, uuid);
        }
    }

    /** Returns the new fame. */
    public int addFame(UUID uuid, int delta) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO yw_cook_fame (uuid, fame) VALUES (?, ?) ON DUPLICATE KEY UPDATE fame = fame + VALUES(fame)")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, delta);
                ps.executeUpdate();
            }
            return fame(connection, uuid);
        }
    }

    public void setFame(UUID uuid, int fame) throws SQLException {
        update("INSERT INTO yw_cook_fame (uuid, fame) VALUES (?, ?) ON DUPLICATE KEY UPDATE fame = VALUES(fame)", uuid, fame);
    }

    private static int fame(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT fame FROM yw_cook_fame WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ---- group order ----

    public Optional<GroupOrder> activeGroup() throws SQLException {
        List<GroupOrder> groups = groups("WHERE status = 'ACTIVE' LIMIT 1");
        return groups.isEmpty() ? Optional.empty() : Optional.of(groups.get(0));
    }

    public List<GroupOrder> expiredActiveGroups(long now) throws SQLException {
        return groups("WHERE status = 'ACTIVE' AND ends_at <= ?", now);
    }

    public List<GroupOrder> fullActiveGroups() throws SQLException {
        return groups("WHERE status = 'ACTIVE' AND progress >= target");
    }

    public List<GroupOrder> doneUnsettledGroups() throws SQLException {
        return groups("WHERE status = 'DONE' AND settled = 0");
    }

    /** Whether a scheduled start at {@code startsAt} must be skipped: already started once, or another group is running. */
    public boolean groupBlocked(long startsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_cook_group WHERE status = 'ACTIVE' OR starts_at = ?")) {
            ps.setLong(1, startsAt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** The new group's id, or empty if one is already running or this start already happened. */
    public OptionalInt startGroup(String recipeId, int target, long startsAt, long endsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_cook_group (recipe_id, target, starts_at, ends_at, status, active_lock) "
                             + "VALUES (?, ?, ?, ?, 'ACTIVE', 1)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, recipeId);
            ps.setInt(2, target);
            ps.setLong(3, startsAt);
            ps.setLong(4, endsAt);
            if (ps.executeUpdate() == 0) {
                return OptionalInt.empty();
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? OptionalInt.of(keys.getInt(1)) : OptionalInt.empty();
            }
        }
    }

    /** How many dishes a group delivery added, and the group's progress afterwards. */
    public record GroupAdd(int added, int progress) {
    }

    /**
     * Adds up to {@code amount} to a running, unexpired group order without passing the target, and the
     * same to the player's contribution, in one transaction (the group row is locked while the remaining
     * amount is read). Empty if the group isn't running any more; {@code added} 0 if it's already full.
     */
    public Optional<GroupAdd> addGroupProgress(int groupId, UUID uuid, String name, int amount, long now) throws SQLException {
        return inTransaction(connection -> {
            int progress;
            int remaining;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT progress, target FROM yw_cook_group WHERE id = ? AND status = 'ACTIVE' AND ends_at > ? FOR UPDATE")) {
                bind(ps, groupId, now);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    progress = rs.getInt(1);
                    remaining = rs.getInt(2) - progress;
                }
            }
            int added = Math.min(amount, remaining);
            if (added <= 0) {
                return Optional.of(new GroupAdd(0, progress));
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE yw_cook_group SET progress = progress + ? WHERE id = ?")) {
                bind(ps, added, groupId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO yw_cook_group_contrib (group_id, uuid, name, amount) VALUES (?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), name = VALUES(name)")) {
                ps.setInt(1, groupId);
                ps.setString(2, uuid.toString());
                ps.setString(3, name);
                ps.setInt(4, added);
                ps.executeUpdate();
            }
            return Optional.of(new GroupAdd(added, progress + added));
        });
    }

    public void undoGroupProgress(int groupId, UUID uuid, int amount) throws SQLException {
        inTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE yw_cook_group SET progress = progress - ? WHERE id = ?")) {
                ps.setInt(1, amount);
                ps.setInt(2, groupId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_cook_group_contrib SET amount = amount - ? WHERE group_id = ? AND uuid = ?")) {
                ps.setInt(1, amount);
                ps.setInt(2, groupId);
                ps.setString(3, uuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** True only for the one caller that flips this full, running group order to DONE. */
    public boolean markGroupDone(int groupId) throws SQLException {
        return update("UPDATE yw_cook_group SET status = 'DONE', active_lock = NULL "
                + "WHERE id = ? AND status = 'ACTIVE' AND progress >= target", groupId) == 1;
    }

    /**
     * True only for the one caller that flips this running group order to FAILED. Unless {@code evenIfFull}
     * (admin 단체종료), a full order is left for {@link #markGroupDone}.
     */
    public boolean markGroupFailed(int groupId, boolean evenIfFull) throws SQLException {
        return update("UPDATE yw_cook_group SET status = 'FAILED', active_lock = NULL WHERE id = ? AND status = 'ACTIVE'"
                + (evenIfFull ? "" : " AND progress < target"), groupId) == 1;
    }

    /** True only for the one caller that gets to pay this DONE group's rewards. */
    public boolean claimSettle(int groupId) throws SQLException {
        return update("UPDATE yw_cook_group SET settled = 1 WHERE id = ? AND status = 'DONE' AND settled = 0", groupId) == 1;
    }

    public int contribution(int groupId, UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT amount FROM yw_cook_group_contrib WHERE group_id = ? AND uuid = ?")) {
            ps.setInt(1, groupId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Largest contribution first. */
    public List<Contribution> contributions(int groupId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid, name, amount FROM yw_cook_group_contrib WHERE group_id = ? AND amount > 0 ORDER BY amount DESC, uuid")) {
            ps.setInt(1, groupId);
            List<Contribution> contributions = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contributions.add(new Contribution(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3)));
                }
            }
            return contributions;
        }
    }

    private List<GroupOrder> groups(String where, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + GROUP_COLUMNS + " FROM yw_cook_group " + where)) {
            bind(ps, params);
            List<GroupOrder> groups = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groups.add(new GroupOrder(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                            rs.getLong(5), rs.getLong(6), rs.getString(7)));
                }
            }
            return groups;
        }
    }

    // ---- cross-server announcements ----

    public long maxAnnouncementId() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM yw_cook_announcements");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Every server (this one included) broadcasts it on its next poll. Values must not contain newlines. */
    public void announce(String key, Map<String, String> args) throws SQLException {
        StringBuilder encoded = new StringBuilder();
        args.forEach((name, value) -> encoded.append(name).append('=').append(value.replace('\n', ' ')).append('\n'));
        update("INSERT INTO yw_cook_announcements (msg_key, args, created_at) VALUES (?, ?, ?)", key, encoded.toString(), System.currentTimeMillis());
    }

    // ponytail: announcement rows are never pruned — a handful per week; add a cleanup if the table ever matters.
    public List<Announcement> announcementsAfter(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, msg_key, args FROM yw_cook_announcements WHERE id > ? ORDER BY id")) {
            ps.setLong(1, id);
            List<Announcement> announcements = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> args = new LinkedHashMap<>();
                    for (String line : rs.getString(3).split("\n")) {
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            args.put(line.substring(0, eq), line.substring(eq + 1));
                        }
                    }
                    announcements.add(new Announcement(rs.getLong(1), rs.getString(2), args));
                }
            }
            return announcements;
        }
    }

    // ---- helpers ----

    private int update(String sql, Object... params) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            ps.setObject(i + 1, param instanceof UUID uuid ? uuid.toString() : param);
        }
    }

    private <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
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
