package com.yeowool.market.questboard;

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
import java.util.Optional;
import java.util.UUID;

/**
 * All quest-board SQL. Every state change that owes someone money writes the
 * matching {@code yw_quest_payouts} row in the same transaction, and every
 * change is conditional on the row still being OPEN, so three servers
 * running the same action at once still move money exactly once.
 */
public final class QuestRepository {

    public record Payout(long id, UUID player, long amount, String reason) {
    }

    private static final String REQUESTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_quest_requests (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                requester CHAR(36) NOT NULL,
                requester_name VARCHAR(16) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                item_label VARCHAR(64) NOT NULL,
                quantity INT NOT NULL,
                delivered INT NOT NULL DEFAULT 0,
                reward_per_item BIGINT NOT NULL,
                status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                INDEX idx_status (status),
                INDEX idx_requester (requester)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String PAYOUTS_DDL = """
            CREATE TABLE IF NOT EXISTS yw_quest_payouts (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                player CHAR(36) NOT NULL,
                amount BIGINT NOT NULL,
                reason VARCHAR(128) NOT NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_player (player)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String COLUMNS = "id, requester, requester_name, item_data, item_label, quantity, delivered, "
            + "reward_per_item, status, created_at, expires_at";

    private final DataSource dataSource;

    public QuestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(REQUESTS_DDL);
            statement.executeUpdate(PAYOUTS_DDL);
        }
    }

    public long insert(UUID requester, String requesterName, ItemStack sample, String itemLabel, int quantity,
                       long rewardPerItem, long now, long expiresAt) throws SQLException {
        String sql = "INSERT INTO yw_quest_requests (requester, requester_name, item_data, item_label, quantity, "
                + "reward_per_item, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, requester.toString());
            ps.setString(2, requesterName);
            ps.setString(3, ItemStackSerializer.serialize(sample));
            ps.setString(4, itemLabel);
            ps.setInt(5, quantity);
            ps.setLong(6, rewardPerItem);
            ps.setLong(7, now);
            ps.setLong(8, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("의뢰 id를 받지 못했습니다");
                }
                return keys.getLong(1);
            }
        }
    }

    public int countOpen(UUID requester) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM yw_quest_requests WHERE requester = ? AND status = 'OPEN'")) {
            ps.setString(1, requester.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<QuestRequest> listOpen(long now, int offset, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM yw_quest_requests WHERE status = 'OPEN' AND expires_at > ? ORDER BY id DESC LIMIT ? OFFSET ?")) {
            ps.setLong(1, now);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            return readAll(ps);
        }
    }

    public List<QuestRequest> listByRequester(UUID requester, int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM yw_quest_requests WHERE requester = ? ORDER BY (status = 'OPEN') DESC, id DESC LIMIT ?")) {
            ps.setString(1, requester.toString());
            ps.setInt(2, limit);
            return readAll(ps);
        }
    }

    public Optional<QuestRequest> find(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT " + COLUMNS + " FROM yw_quest_requests WHERE id = ?")) {
            ps.setLong(1, id);
            List<QuestRequest> found = readAll(ps);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    /**
     * Records {@code amount} delivered items and ledgers the deliverer's reward
     * in one transaction. The UPDATE only matches while the request is OPEN,
     * unexpired and still needs at least {@code amount} — so concurrent
     * deliveries from different servers can never over-fill it. The status
     * assignment comes first on purpose: MySQL evaluates SET left to right, so
     * it must read {@code delivered} before it is incremented.
     *
     * @return the reward ledgered for the deliverer, or -1 if nothing was recorded
     */
    public long deliver(long id, UUID deliverer, int amount, long now) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE yw_quest_requests SET status = IF(delivered + ? >= quantity, 'COMPLETED', 'OPEN'), "
                                + "delivered = delivered + ? "
                                + "WHERE id = ? AND status = 'OPEN' AND expires_at > ? AND delivered + ? <= quantity")) {
                    ps.setInt(1, amount);
                    ps.setInt(2, amount);
                    ps.setLong(3, id);
                    ps.setLong(4, now);
                    ps.setInt(5, amount);
                    if (ps.executeUpdate() != 1) {
                        connection.rollback();
                        return -1;
                    }
                }
                long reward;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT reward_per_item FROM yw_quest_requests WHERE id = ?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        reward = rs.getLong(1) * amount;
                    }
                }
                insertPayout(connection, deliverer, reward, "의뢰 #" + id + " 납품 " + amount + "개");
                connection.commit();
                return reward;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Cancels an OPEN request owned by {@code requester} and ledgers the refund; -1 if it isn't open or isn't theirs. */
    public long cancel(long id, UUID requester) throws SQLException {
        return close(id, requester, "CANCELLED", "의뢰 #" + id + " 취소 환불");
    }

    /** Expires up to 50 overdue OPEN requests, ledgering each refund; returns how many this call closed. */
    public int expireDue(long now) throws SQLException {
        List<Long> due = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id FROM yw_quest_requests WHERE status = 'OPEN' AND expires_at <= ? LIMIT 50")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(rs.getLong(1));
                }
            }
        }
        int closed = 0;
        for (long id : due) {
            if (close(id, null, "EXPIRED", "의뢰 #" + id + " 만료 환불") >= 0) {
                closed++;
            }
        }
        return closed;
    }

    /** Locks the row, and only if it is still OPEN (and owned by {@code requester} when given) closes it and ledgers the refund. */
    private long close(long id, UUID requester, String newStatus, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID owner;
                long refund;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT requester, quantity, delivered, reward_per_item FROM yw_quest_requests "
                                + "WHERE id = ? AND status = 'OPEN' FOR UPDATE")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return -1;
                        }
                        owner = UUID.fromString(rs.getString(1));
                        refund = QuestBoardRules.refund(rs.getInt(2), rs.getInt(3), rs.getLong(4));
                    }
                }
                if (requester != null && !requester.equals(owner)) {
                    connection.rollback();
                    return -1;
                }
                try (PreparedStatement ps = connection.prepareStatement("UPDATE yw_quest_requests SET status = ? WHERE id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setLong(2, id);
                    ps.executeUpdate();
                }
                if (refund > 0) {
                    insertPayout(connection, owner, refund, reason);
                }
                connection.commit();
                return refund;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void insertPayout(UUID player, long amount, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insertPayout(connection, player, amount, reason);
        }
    }

    private static void insertPayout(Connection connection, UUID player, long amount, String reason) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO yw_quest_payouts (player, amount, reason, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setLong(2, amount);
            ps.setString(3, reason);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<Payout> pendingPayouts(UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, player, amount, reason FROM yw_quest_payouts WHERE player = ? ORDER BY id")) {
            ps.setString(1, player.toString());
            List<Payout> payouts = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.add(new Payout(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getLong(3), rs.getString(4)));
                }
            }
            return payouts;
        }
    }

    /** True only for the single caller whose DELETE actually removed the row — that caller owns the payout. */
    public boolean deletePayout(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_quest_payouts WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static List<QuestRequest> readAll(PreparedStatement ps) throws SQLException {
        List<QuestRequest> requests = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                requests.add(new QuestRequest(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("requester")),
                        rs.getString("requester_name"),
                        ItemStackSerializer.deserialize(rs.getString("item_data")),
                        rs.getString("item_label"),
                        rs.getInt("quantity"),
                        rs.getInt("delivered"),
                        rs.getLong("reward_per_item"),
                        rs.getString("status"),
                        rs.getLong("created_at"),
                        rs.getLong("expires_at")));
            }
        }
        return requests;
    }
}
