package com.yeowool.community.vote;

import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 추천 보상 tables. Every counted vote is one {@code yw_votes} row — the two unique keys make
 * "one vote per player per day" hold across servers, a row with {@code uuid} NULL is a vote for a
 * name that hasn't joined yet (paid when {@link #claimPending} flips it), and the auto-increment
 * id doubles as the cross-server announcement cursor. Blocking JDBC — worker threads only.
 */
final class VoteRepository {

    record Reward(int threshold, long on, long stardust, List<ItemStack> items) {
    }

    /** {@code uuid} null while pending; {@code total} = the voter's counted votes up to and including this one. */
    record Vote(long id, String name, UUID uuid, int total) {
    }

    record Ranked(String name, int votes) {
    }

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_votes (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                uuid CHAR(36) NULL,
                name VARCHAR(16) NOT NULL,
                name_key VARCHAR(16) NOT NULL,
                day VARCHAR(10) NOT NULL,
                service VARCHAR(64) NOT NULL,
                created_at BIGINT NOT NULL,
                UNIQUE KEY uq_vote_uuid_day (uuid, day),
                UNIQUE KEY uq_vote_name_day (name_key, day)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_vote_milestones_paid (
                uuid CHAR(36) NOT NULL,
                threshold INT NOT NULL,
                paid_at BIGINT NOT NULL,
                PRIMARY KEY (uuid, threshold)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_vote_rewards (
                threshold INT NOT NULL PRIMARY KEY,
                on_amount BIGINT NOT NULL DEFAULT 0,
                stardust BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_vote_reward_items (
                threshold INT NOT NULL,
                slot INT NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                PRIMARY KEY (threshold, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    /** threshold → {온, 별조각}; seeded only into an empty rewards table. */
    private static final Map<Integer, long[]> DEFAULTS = Map.of(
            VoteRules.EVERY_VOTE, new long[]{5000, 3},
            10, new long[]{0, 20},
            30, new long[]{0, 50},
            100, new long[]{0, 150});

    private final DataSource dataSource;

    VoteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM yw_vote_rewards")) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
            // IGNORE: another server may be seeding at the same moment
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT IGNORE INTO yw_vote_rewards (threshold, on_amount, stardust) VALUES (?, ?, ?)")) {
                for (var entry : DEFAULTS.entrySet()) {
                    insert.setInt(1, entry.getKey());
                    insert.setLong(2, entry.getValue()[0]);
                    insert.setLong(3, entry.getValue()[1]);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
    }

    // ---- votes ----

    /** Most recently seen core player row with this username (MySQL's default collation ignores case). */
    Optional<UUID> findUuidByName(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid FROM yw_players WHERE username = ? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    /** False if this player (or name) already has a counted vote that day. */
    // ponytail: setting_value isn't indexed — a scan of yw_player_settings per Korean-name vote; index it if that table gets huge.
    /** Owner of a 한글 닉네임 ({@code nickname.korean} player setting, see KoreanNicknameManager). */
    Optional<UUID> findUuidByKoreanNickname(String nickname) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid FROM yw_player_settings WHERE setting_key = 'nickname.korean' AND setting_value = ? LIMIT 1")) {
            ps.setString(1, nickname);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    boolean insertVote(UUID uuid, String name, String nameKey, String day, String service) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_votes (uuid, name, name_key, day, service, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid == null ? null : uuid.toString());
            ps.setString(2, name);
            ps.setString(3, nameKey);
            ps.setString(4, day);
            ps.setString(5, service.length() > 64 ? service.substring(0, 64) : service);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException duplicate) {
            return false;
        }
    }

    int totalVotes(UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM yw_votes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    boolean votedOn(UUID uuid, String day) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM yw_votes WHERE uuid = ? AND day = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ponytail: pending rows for names that never join (typos) stay forever — harmless, a few bytes each.
    List<Long> pendingIds(String nameKey) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM yw_votes WHERE name_key = ? AND uuid IS NULL ORDER BY id")) {
            ps.setString(1, nameKey);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
        }
    }

    /**
     * Assigns a pending vote to {@code uuid}; true only for the one call that flipped it (the WHERE
     * excludes claimed rows, so Connector/J's found-rows count equals the changed-rows count). A
     * pending vote on a day this player already has a counted vote for is dropped instead.
     */
    boolean claimPending(long id, UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_votes SET uuid = ? WHERE id = ? AND uuid IS NULL")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, id);
                return ps.executeUpdate() == 1;
            } catch (SQLIntegrityConstraintViolationException duplicateDay) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_votes WHERE id = ? AND uuid IS NULL")) {
                    delete.setLong(1, id);
                    delete.executeUpdate();
                }
                return false;
            }
        }
    }

    /** True for the one call that records this milestone as paid. */
    boolean markMilestonePaid(UUID uuid, int threshold) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_vote_milestones_paid (uuid, threshold, paid_at) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, threshold);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException alreadyPaid) {
            return false;
        }
    }

    long maxVoteId() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM yw_votes");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    List<Vote> votesAfter(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT v.id, v.name, v.uuid, (SELECT COUNT(*) FROM yw_votes w WHERE w.uuid = v.uuid AND w.id <= v.id) "
                             + "FROM yw_votes v WHERE v.id > ? ORDER BY v.id")) {
            ps.setLong(1, id);
            List<Vote> votes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuid = rs.getString(3);
                    votes.add(new Vote(rs.getLong(1), rs.getString(2), uuid == null ? null : UUID.fromString(uuid), rs.getInt(4)));
                }
            }
            return votes;
        }
    }

    /** Players with the most counted votes on days in [fromDay, toDay). */
    List<Ranked> topVoters(String fromDay, String toDay, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COALESCE(MAX(p.username), MAX(v.name)), COUNT(*) AS votes FROM yw_votes v "
                             + "LEFT JOIN yw_players p ON p.uuid = v.uuid "
                             + "WHERE v.uuid IS NOT NULL AND v.day >= ? AND v.day < ? "
                             + "GROUP BY v.uuid ORDER BY votes DESC, MIN(v.id) LIMIT ?")) {
            ps.setString(1, fromDay);
            ps.setString(2, toDay);
            ps.setInt(3, limit);
            List<Ranked> ranked = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ranked.add(new Ranked(rs.getString(1), rs.getInt(2)));
                }
            }
            return ranked;
        }
    }

    // ---- rewards ----

    /** threshold → reward, ascending ({@link VoteRules#EVERY_VOTE} first). */
    TreeMap<Integer, Reward> loadRewards() throws SQLException {
        TreeMap<Integer, List<ItemStack>> items = new TreeMap<>();
        TreeMap<Integer, Reward> rewards = new TreeMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT threshold, item_data FROM yw_vote_reward_items ORDER BY threshold, slot");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.computeIfAbsent(rs.getInt(1), key -> new ArrayList<>())
                            .add(ItemStackSerializer.deserialize(rs.getString(2)));
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT threshold, on_amount, stardust FROM yw_vote_rewards");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int threshold = rs.getInt(1);
                    rewards.put(threshold, new Reward(threshold, rs.getLong(2), rs.getLong(3),
                            List.copyOf(items.getOrDefault(threshold, List.of()))));
                }
            }
        }
        return rewards;
    }

    /** False if that milestone already exists. */
    boolean addMilestone(int threshold) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_vote_rewards (threshold, on_amount, stardust) VALUES (?, 0, 0)")) {
            ps.setInt(1, threshold);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException exists) {
            return false;
        }
    }

    /**
     * False if there was no such milestone. One transaction, reward row first — it's the row
     * {@link #saveItems} locks, so a concurrent item save either finishes before or sees it gone.
     * Already-paid records stay, so re-adding it never pays twice.
     */
    boolean deleteMilestone(int threshold) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean deleted;
                try (PreparedStatement reward = connection.prepareStatement("DELETE FROM yw_vote_rewards WHERE threshold = ?")) {
                    reward.setInt(1, threshold);
                    deleted = reward.executeUpdate() == 1;
                }
                try (PreparedStatement items = connection.prepareStatement("DELETE FROM yw_vote_reward_items WHERE threshold = ?")) {
                    items.setInt(1, threshold);
                    items.executeUpdate();
                }
                connection.commit();
                return deleted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Sets 별조각 ({@code stardust}) or 온; only updates an existing row, so an edit racing a delete can't resurrect it. */
    void saveAmount(int threshold, boolean stardust, long amount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(stardust
                     ? "UPDATE yw_vote_rewards SET stardust = ? WHERE threshold = ?"
                     : "UPDATE yw_vote_rewards SET on_amount = ? WHERE threshold = ?")) {
            ps.setLong(1, amount);
            ps.setInt(2, threshold);
            ps.executeUpdate();
        }
    }

    /** Wipes and re-inserts one reward's items; false (nothing saved) if the reward was deleted meanwhile. */
    boolean saveItems(int threshold, List<ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement exists = connection.prepareStatement(
                        "SELECT 1 FROM yw_vote_rewards WHERE threshold = ? FOR UPDATE")) {
                    exists.setInt(1, threshold);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_vote_reward_items WHERE threshold = ?")) {
                    delete.setInt(1, threshold);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO yw_vote_reward_items (threshold, slot, item_data) VALUES (?, ?, ?)")) {
                    for (int slot = 0; slot < items.size(); slot++) {
                        insert.setInt(1, threshold);
                        insert.setInt(2, slot);
                        insert.setString(3, ItemStackSerializer.serialize(items.get(slot)));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }
}
