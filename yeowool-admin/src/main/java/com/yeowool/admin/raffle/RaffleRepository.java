package com.yeowool.admin.raffle;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RaffleRepository {

    public record WinRow(String itemId, UUID uuid, int winCount) {
    }

    private final DataSource dataSource;

    public RaffleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Map<UUID, Integer>> loadAll() throws SQLException {
        Map<String, Map<UUID, Integer>> history = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT item_id, uuid, win_count FROM yw_raffle_wins");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                history.computeIfAbsent(rs.getString("item_id"), k -> new HashMap<>())
                        .put(UUID.fromString(rs.getString("uuid")), rs.getInt("win_count"));
            }
        }
        return history;
    }

    public void incrementWin(String itemId, UUID uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO yw_raffle_wins (item_id, uuid, win_count) VALUES (?, ?, 1)
                     ON DUPLICATE KEY UPDATE win_count = win_count + 1
                     """)) {
            statement.setString(1, itemId);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }
}
