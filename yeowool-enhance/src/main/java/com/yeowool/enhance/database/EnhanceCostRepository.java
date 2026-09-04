package com.yeowool.enhance.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class EnhanceCostRepository {

    public record CostRow(long currency, String materialId, int materialAmount) {
    }

    private final DataSource dataSource;

    public EnhanceCostRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Integer, CostRow> loadAll() throws SQLException {
        Map<Integer, CostRow> rows = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT level, currency, material_id, material_amount FROM yw_enhance_costs");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                rows.put(rs.getInt("level"), new CostRow(rs.getLong("currency"), rs.getString("material_id"), rs.getInt("material_amount")));
            }
        }
        return rows;
    }

    public void upsert(int level, long currency, String materialId, int materialAmount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO yw_enhance_costs (level, currency, material_id, material_amount) VALUES (?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE currency = VALUES(currency), material_id = VALUES(material_id), material_amount = VALUES(material_amount)
                     """)) {
            statement.setInt(1, level);
            statement.setLong(2, currency);
            statement.setString(3, materialId);
            statement.setInt(4, materialAmount);
            statement.executeUpdate();
        }
    }
}
