package com.yeowool.core.data.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PayoutRepository {

    public record Payout(long id, UUID player, long amount, String source, String reason) {
    }

    private final DataSource dataSource;

    public PayoutRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(UUID player, long amount, String source, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO yw_payouts (player, amount, source, reason, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, player.toString());
            ps.setLong(2, amount);
            ps.setString(3, truncate(source, 32));
            ps.setString(4, truncate(reason, 128));
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<Payout> pending(UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, player, amount, source, reason FROM yw_payouts WHERE player = ? ORDER BY id")) {
            ps.setString(1, player.toString());
            List<Payout> payouts = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payouts.add(new Payout(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getLong(3), rs.getString(4), rs.getString(5)));
                }
            }
            return payouts;
        }
    }

    /** True only for the one caller whose DELETE removed the row — that caller owns the payout. */
    public boolean delete(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM yw_payouts WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
