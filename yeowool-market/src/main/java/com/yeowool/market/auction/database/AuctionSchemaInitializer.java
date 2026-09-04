package com.yeowool.market.auction.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class AuctionSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_auction_listings (
                id CHAR(36) NOT NULL PRIMARY KEY,
                seller_uuid CHAR(36) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                starting_bid BIGINT NOT NULL,
                buy_now_price BIGINT NOT NULL DEFAULT 0,
                current_bid BIGINT NOT NULL DEFAULT 0,
                current_bidder_uuid CHAR(36) NULL,
                currency VARCHAR(8) NOT NULL DEFAULT 'ON',
                end_at BIGINT NOT NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_seller (seller_uuid),
                INDEX idx_end_at (end_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private AuctionSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(DDL);
            }
            // Additive migration for servers whose yw_auction_listings predates cash-currency support.
            addColumnIfMissing(connection, "yw_auction_listings", "currency", "VARCHAR(8) NOT NULL DEFAULT 'ON'");
        }
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
