package com.yeowool.admin.coupon.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class CouponSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_coupons (
                code VARCHAR(64) NOT NULL PRIMARY KEY,
                display_code VARCHAR(64) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_coupon_redemptions (
                code VARCHAR(64) NOT NULL,
                uuid CHAR(36) NOT NULL,
                redeemed_at BIGINT NOT NULL,
                PRIMARY KEY (code, uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private CouponSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
        }
    }
}
