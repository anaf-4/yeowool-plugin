package com.yeowool.admin.cashpackage.repository;

import com.yeowool.admin.cashpackage.CashPackage;
import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Blocking JDBC access to {@code yw_cash_packages}. Must only be called off the main thread. */
public final class CashPackageRepository {

    private final DataSource dataSource;

    public CashPackageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Keyed by the normalized (lowercased) name — see {@link com.yeowool.admin.cashpackage.CashPackageManager}. */
    public Map<String, CashPackage> loadAll() throws SQLException {
        Map<String, CashPackage> packages = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT name, display_name, item_data, created_at FROM yw_cash_packages");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                packages.put(rs.getString("name"), new CashPackage(
                        rs.getString("display_name"),
                        List.of(ItemStackSerializer.deserializeArray(rs.getString("item_data"))),
                        rs.getLong("created_at")
                ));
            }
        }
        return packages;
    }

    public void upsert(String normalizedName, CashPackage pkg) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_cash_packages (name, display_name, item_data, created_at) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), item_data = VALUES(item_data)")) {
            upsert.setString(1, normalizedName);
            upsert.setString(2, pkg.name());
            upsert.setString(3, ItemStackSerializer.serializeArray(pkg.items().toArray(new ItemStack[0])));
            upsert.setLong(4, pkg.createdAt());
            upsert.executeUpdate();
        }
    }

    public void delete(String normalizedName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_cash_packages WHERE name = ?")) {
            delete.setString(1, normalizedName);
            delete.executeUpdate();
        }
    }
}
