package com.yeowool.admin.coupon.repository;

import com.yeowool.admin.coupon.Coupon;
import com.yeowool.core.util.ItemStackSerializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_coupons}/{@code yw_coupon_redemptions}. Must only be called off the main thread. */
public final class CouponRepository {

    private final DataSource dataSource;

    public CouponRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Keyed by the normalized (lowercased) code — see {@link com.yeowool.admin.coupon.CouponManager}. */
    public Map<String, Coupon> loadAllCoupons() throws SQLException {
        Map<String, Coupon> coupons = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT code, display_code, item_data, created_at, expires_at FROM yw_coupons");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("code");
                coupons.put(code, new Coupon(
                        rs.getString("display_code"),
                        ItemStackSerializer.deserialize(rs.getString("item_data")),
                        rs.getLong("created_at"),
                        rs.getLong("expires_at")
                ));
            }
        }
        return coupons;
    }

    /** Set entries are {@code "<normalizedCode>:<uuid>"}. */
    public Set<String> loadAllRedemptions() throws SQLException {
        Set<String> redemptions = new java.util.HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT code, uuid FROM yw_coupon_redemptions");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                redemptions.add(rs.getString("code") + ":" + rs.getString("uuid"));
            }
        }
        return redemptions;
    }

    public void upsert(String normalizedCode, Coupon coupon) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_coupons (code, display_code, item_data, created_at, expires_at) VALUES (?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE display_code = VALUES(display_code), item_data = VALUES(item_data), "
                             + "created_at = VALUES(created_at), expires_at = VALUES(expires_at)")) {
            upsert.setString(1, normalizedCode);
            upsert.setString(2, coupon.code());
            upsert.setString(3, ItemStackSerializer.serialize(coupon.rewardItem()));
            upsert.setLong(4, coupon.createdAt());
            upsert.setLong(5, coupon.expiresAt());
            upsert.executeUpdate();
        }
    }

    public void delete(String normalizedCode) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_coupons WHERE code = ?")) {
                delete.setString(1, normalizedCode);
                delete.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_coupon_redemptions WHERE code = ?")) {
                delete.setString(1, normalizedCode);
                delete.executeUpdate();
            }
        }
    }

    public void insertRedemption(String normalizedCode, UUID uuid, long redeemedAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT IGNORE INTO yw_coupon_redemptions (code, uuid, redeemed_at) VALUES (?, ?, ?)")) {
            insert.setString(1, normalizedCode);
            insert.setString(2, uuid.toString());
            insert.setLong(3, redeemedAt);
            insert.executeUpdate();
        }
    }
}
