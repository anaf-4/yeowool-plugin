package com.yeowool.life.donation;

import com.yeowool.life.donation.DonationRules.Candidate;
import com.yeowool.life.donation.DonationRules.GoalSpec;
import com.yeowool.life.surprise.SurpriseEventType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Shared (all three servers) 기부 프로젝트 rows. Every step that must happen once — starting a week's
 * project, clamping a donation to what's left, the DONE/FAILED flips, settlement and milestone
 * announcements — is a row lock, conditional UPDATE or unique-key INSERT, so only one caller wins.
 * Blocking JDBC: worker thread only.
 */
public final class DonationRepository {

    public record Goal(int slot, String itemKey, byte[] display, int target, int progress, int points) {
    }

    /** {@code goals} is empty for the list queries that don't need them. */
    public record Project(int id, String name, long startsAt, long endsAt, String status, SurpriseEventType buff,
                          List<Goal> goals) {
    }

    public record Contribution(UUID player, String name, long points) {
    }

    /** One donation: how many were added and the goal's progress before it. */
    public record Add(int added, int before, int target, int points) {
    }

    public record Announcement(long id, String key, Map<String, String> args) {
    }

    // active_lock is 1 while ACTIVE and NULL afterwards: its unique key allows only one running project,
    // and the unique starts_at stops the three servers starting the same week twice.
    private static final List<String> DDL = List.of("""
            CREATE TABLE IF NOT EXISTS yw_donation_project (
                id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(64) NOT NULL,
                starts_at BIGINT NOT NULL,
                ends_at BIGINT NOT NULL,
                status VARCHAR(10) NOT NULL,
                buff VARCHAR(16) NOT NULL,
                buff_ends_at BIGINT NOT NULL DEFAULT 0,
                announced_pct INT NOT NULL DEFAULT 0,
                settled TINYINT(1) NOT NULL DEFAULT 0,
                active_lock TINYINT(1) NULL,
                UNIQUE KEY uk_starts_at (starts_at),
                UNIQUE KEY uk_active (active_lock)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_donation_goal (
                project_id INT NOT NULL,
                slot TINYINT NOT NULL,
                item_key VARCHAR(128) NOT NULL,
                display_item BLOB NULL,
                target INT NOT NULL,
                progress INT NOT NULL DEFAULT 0,
                points INT NOT NULL,
                PRIMARY KEY (project_id, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_donation_contrib (
                project_id INT NOT NULL,
                uuid CHAR(36) NOT NULL,
                name VARCHAR(16) NOT NULL,
                points BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (project_id, uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_donation_schedule (
                slot TINYINT NOT NULL PRIMARY KEY,
                name VARCHAR(64) NOT NULL,
                buff VARCHAR(16) NULL,
                item_key VARCHAR(128) NOT NULL,
                display_item BLOB NULL,
                target INT NOT NULL,
                points INT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """, """
            CREATE TABLE IF NOT EXISTS yw_donation_announcements (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                msg_key VARCHAR(64) NOT NULL,
                args VARCHAR(1024) NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

    private static final String PROJECT_COLUMNS = "SELECT id, name, starts_at, ends_at, status, buff FROM yw_donation_project ";

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final DataSource dataSource;

    public DonationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
        }
    }

    // ---- projects ----

    /** The running project with its goals. */
    public Optional<Project> active() throws SQLException {
        return withGoals(projects("WHERE status = 'ACTIVE' LIMIT 1"));
    }

    /** The running project, else the most recent one (for 순위). */
    public Optional<Project> currentOrLatest() throws SQLException {
        Optional<Project> active = active();
        return active.isPresent() ? active : withGoals(projects("ORDER BY id DESC LIMIT 1"));
    }

    public Optional<Project> project(int id) throws SQLException {
        List<Project> projects = projects("WHERE id = " + id);
        return projects.isEmpty() ? Optional.empty() : Optional.of(projects.get(0));
    }

    public List<Project> activeProjects() throws SQLException {
        return projects("WHERE status = 'ACTIVE'");
    }

    public List<Project> doneUnsettled() throws SQLException {
        return projects("WHERE status = 'DONE' AND settled = 0");
    }

    /** The latest project (to avoid repeating its pool entry and to rotate its buff). */
    public Optional<Project> latest() throws SQLException {
        List<Project> latest = projects("ORDER BY id DESC LIMIT 1");
        return latest.isEmpty() ? Optional.empty() : Optional.of(latest.get(0));
    }

    /** Whether a scheduled start at {@code startsAt} must be skipped: already started once, or another project is running. */
    public boolean blocked(long startsAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_donation_project WHERE status = 'ACTIVE' OR starts_at = ?")) {
            ps.setLong(1, startsAt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Starts {@code candidate} with its goals in one transaction, and — when it came from the reservation —
     * clears the reservation in the same one. Empty if a project is running or this start already happened.
     */
    public OptionalInt start(Candidate candidate, SurpriseEventType buff, long startsAt, long endsAt, boolean fromSchedule)
            throws SQLException {
        return inTransaction(connection -> {
            int id;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_donation_project (name, starts_at, ends_at, status, buff, active_lock) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, 1)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, candidate.name());
                ps.setLong(2, startsAt);
                ps.setLong(3, endsAt);
                ps.setString(4, buff.key());
                if (ps.executeUpdate() == 0) {
                    return OptionalInt.empty();
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("기부 프로젝트 id를 받지 못했습니다");
                    }
                    id = keys.getInt(1);
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO yw_donation_goal (project_id, slot, item_key, display_item, target, points) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (int slot = 0; slot < candidate.goals().size(); slot++) {
                    GoalSpec goal = candidate.goals().get(slot);
                    ps.setInt(1, id);
                    ps.setInt(2, slot);
                    ps.setString(3, goal.itemKey());
                    ps.setBytes(4, goal.display());
                    ps.setInt(5, goal.target());
                    ps.setInt(6, goal.points());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            if (fromSchedule) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM yw_donation_schedule");
                }
            }
            return OptionalInt.of(id);
        });
    }

    /**
     * Adds up to {@code amount} to a goal of a running, unexpired project without passing its target, and
     * {@code added × points} to the player's score, in one transaction (the rows are locked while the
     * remaining amount is read). Empty if the project isn't running any more; {@code added} 0 if the goal is full.
     */
    public Optional<Add> addProgress(int projectId, int slot, String itemKey, UUID uuid, String name, int amount, long now)
            throws SQLException {
        return inTransaction(connection -> {
            int target;
            int progress;
            int points;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT g.target, g.progress, g.points FROM yw_donation_goal g JOIN yw_donation_project p ON p.id = g.project_id "
                            + "WHERE g.project_id = ? AND g.slot = ? AND g.item_key = ? AND p.status = 'ACTIVE' AND p.ends_at > ? FOR UPDATE")) {
                bind(ps, projectId, slot, itemKey, now);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    target = rs.getInt(1);
                    progress = rs.getInt(2);
                    points = rs.getInt(3);
                }
            }
            int added = DonationRules.clamp(amount, target, progress);
            if (added <= 0) {
                return Optional.of(new Add(0, progress, target, points));
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_donation_goal SET progress = progress + ? WHERE project_id = ? AND slot = ?")) {
                bind(ps, added, projectId, slot);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO yw_donation_contrib (project_id, uuid, name, points) VALUES (?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE points = points + VALUES(points), name = VALUES(name)")) {
                bind(ps, projectId, uuid, name, (long) added * points);
                ps.executeUpdate();
            }
            return Optional.of(new Add(added, progress, target, points));
        });
    }

    public void undoProgress(int projectId, int slot, UUID uuid, int amount, int points) throws SQLException {
        inTransaction(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_donation_goal SET progress = progress - ? WHERE project_id = ? AND slot = ? AND progress >= ?")) {
                bind(ps, amount, projectId, slot, amount);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_donation_contrib SET points = points - ? WHERE project_id = ? AND uuid = ?")) {
                bind(ps, (long) amount * points, projectId, uuid);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<Goal> goals(int projectId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT slot, item_key, display_item, target, progress, points FROM yw_donation_goal WHERE project_id = ? ORDER BY slot")) {
            ps.setInt(1, projectId);
            List<Goal> goals = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    goals.add(new Goal(rs.getInt(1), rs.getString(2), rs.getBytes(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
            return goals;
        }
    }

    /** True only for the one caller that flips this running, fully donated project to DONE (its buff runs until {@code buffEndsAt}). */
    public boolean markDone(int projectId, long buffEndsAt) throws SQLException {
        return update("UPDATE yw_donation_project SET status = 'DONE', active_lock = NULL, buff_ends_at = ? "
                + "WHERE id = ? AND status = 'ACTIVE' AND EXISTS (SELECT 1 FROM yw_donation_goal WHERE project_id = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM yw_donation_goal WHERE project_id = ? AND progress < target)",
                buffEndsAt, projectId, projectId, projectId) == 1;
    }

    /** True only for the one caller that flips this running project to FAILED; unless {@code evenIfFull}, a full one is left for {@link #markDone}. */
    public boolean markFailed(int projectId, boolean evenIfFull) throws SQLException {
        return update("UPDATE yw_donation_project SET status = 'FAILED', active_lock = NULL WHERE id = ? AND status = 'ACTIVE'"
                + (evenIfFull ? "" : " AND EXISTS (SELECT 1 FROM yw_donation_goal WHERE project_id = ? AND progress < target)"),
                evenIfFull ? new Object[]{projectId} : new Object[]{projectId, projectId}) == 1;
    }

    /** True only for the one caller that gets to pay this DONE project's rewards. */
    public boolean claimSettle(int projectId) throws SQLException {
        return update("UPDATE yw_donation_project SET settled = 1 WHERE id = ? AND status = 'DONE' AND settled = 0", projectId) == 1;
    }

    /** True only for the one caller that announces {@code percent} (or a higher milestone came first). */
    public boolean claimMilestone(int projectId, int percent) throws SQLException {
        return update("UPDATE yw_donation_project SET announced_pct = ? WHERE id = ? AND status = 'ACTIVE' AND announced_pct < ?",
                percent, projectId, percent) == 1;
    }

    /** Achieved projects whose buff is still running: buff → latest end. */
    public Map<SurpriseEventType, Long> activeBuffs(long now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT buff, MAX(buff_ends_at) FROM yw_donation_project WHERE status = 'DONE' AND buff_ends_at > ? GROUP BY buff")) {
            ps.setLong(1, now);
            Map<SurpriseEventType, Long> buffs = new EnumMap<>(SurpriseEventType.class);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long endsAt = rs.getLong(2);
                    SurpriseEventType.byKey(rs.getString(1)).ifPresent(type -> buffs.put(type, endsAt));
                }
            }
            return buffs;
        }
    }

    // ---- contributions ----

    public long points(int projectId, UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT points FROM yw_donation_contrib WHERE project_id = ? AND uuid = ?")) {
            bind(ps, projectId, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Most points first. */
    public List<Contribution> contributions(int projectId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid, name, points FROM yw_donation_contrib WHERE project_id = ? AND points > 0 ORDER BY points DESC, uuid")) {
            ps.setInt(1, projectId);
            List<Contribution> contributions = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contributions.add(new Contribution(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                }
            }
            return contributions;
        }
    }

    // ---- reservation (/기부관리 예약) ----

    public Optional<Candidate> schedule() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT name, buff, item_key, display_item, target, points FROM yw_donation_schedule ORDER BY slot");
             ResultSet rs = ps.executeQuery()) {
            String name = null;
            SurpriseEventType buff = null;
            List<GoalSpec> goals = new ArrayList<>();
            while (rs.next()) {
                name = rs.getString(1);
                buff = SurpriseEventType.byKey(rs.getString(2)).orElse(null);
                goals.add(new GoalSpec(rs.getString(3), rs.getBytes(4), rs.getInt(5), rs.getInt(6)));
            }
            return goals.isEmpty() ? Optional.empty() : Optional.of(new Candidate(name, buff, goals));
        }
    }

    /** Replaces the reservation; {@code candidate} null clears it. */
    public void saveSchedule(Candidate candidate) throws SQLException {
        inTransaction(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM yw_donation_schedule");
            }
            if (candidate == null) {
                return null;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO yw_donation_schedule (slot, name, buff, item_key, display_item, target, points) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (int slot = 0; slot < candidate.goals().size(); slot++) {
                    GoalSpec goal = candidate.goals().get(slot);
                    ps.setInt(1, slot);
                    ps.setString(2, candidate.name());
                    ps.setString(3, candidate.buff() == null ? null : candidate.buff().key());
                    ps.setString(4, goal.itemKey());
                    ps.setBytes(5, goal.display());
                    ps.setInt(6, goal.target());
                    ps.setInt(7, goal.points());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    // ---- cross-server announcements ----

    public long maxAnnouncementId() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM yw_donation_announcements");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Every server (this one included) broadcasts it on its next poll. Values must not contain newlines. */
    public void announce(String key, Map<String, String> args) throws SQLException {
        StringBuilder encoded = new StringBuilder();
        args.forEach((name, value) -> encoded.append(name).append('=').append(value.replace('\n', ' ')).append('\n'));
        update("INSERT INTO yw_donation_announcements (msg_key, args, created_at) VALUES (?, ?, ?)",
                key, encoded.toString(), System.currentTimeMillis());
    }

    // ponytail: announcement rows are never pruned — a handful per week; add a cleanup if the table ever matters.
    public List<Announcement> announcementsAfter(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, msg_key, args FROM yw_donation_announcements WHERE id > ? ORDER BY id")) {
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

    private List<Project> projects(String where) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(PROJECT_COLUMNS + where);
             ResultSet rs = ps.executeQuery()) {
            List<Project> projects = new ArrayList<>();
            while (rs.next()) {
                projects.add(new Project(rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getString(5),
                        SurpriseEventType.byKey(rs.getString(6)).orElse(SurpriseEventType.LAND_XP), List.of()));
            }
            return projects;
        }
    }

    private Optional<Project> withGoals(List<Project> projects) throws SQLException {
        if (projects.isEmpty()) {
            return Optional.empty();
        }
        Project p = projects.get(0);
        return Optional.of(new Project(p.id(), p.name(), p.startsAt(), p.endsAt(), p.status(), p.buff(), goals(p.id())));
    }

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
