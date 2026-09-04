package com.yeowool.market.auction.repository;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.util.ItemStackSerializer;
import com.yeowool.market.auction.AuctionListing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuctionRepository {

    private final DataSource dataSource;

    public AuctionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<AuctionListing> loadAll() throws SQLException {
        List<AuctionListing> listings = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, seller_uuid, item_data, starting_bid, buy_now_price, current_bid, "
                             + "current_bidder_uuid, currency, end_at, created_at FROM yw_auction_listings");
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                String bidderStr = rs.getString("current_bidder_uuid");
                listings.add(new AuctionListing(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("seller_uuid")),
                        ItemStackSerializer.deserialize(rs.getString("item_data")),
                        rs.getLong("starting_bid"),
                        rs.getLong("buy_now_price"),
                        rs.getLong("current_bid"),
                        bidderStr == null ? null : UUID.fromString(bidderStr),
                        CurrencyType.valueOf(rs.getString("currency")),
                        rs.getLong("end_at"),
                        rs.getLong("created_at")
                ));
            }
        }
        return listings;
    }

    public void insert(AuctionListing listing) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_auction_listings (id, seller_uuid, item_data, starting_bid, buy_now_price, "
                             + "current_bid, current_bidder_uuid, currency, end_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, listing.id().toString());
            insert.setString(2, listing.seller().toString());
            insert.setString(3, ItemStackSerializer.serialize(listing.item()));
            insert.setLong(4, listing.startingBid());
            insert.setLong(5, listing.buyNowPrice());
            insert.setLong(6, listing.currentBid());
            insert.setString(7, listing.currentBidder() == null ? null : listing.currentBidder().toString());
            insert.setString(8, listing.currency().name());
            insert.setLong(9, listing.endAtMillis());
            insert.setLong(10, listing.createdAt());
            insert.executeUpdate();
        }
    }

    public void updateBid(UUID id, long currentBid, UUID bidder) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_auction_listings SET current_bid = ?, current_bidder_uuid = ? WHERE id = ?")) {
            update.setLong(1, currentBid);
            update.setString(2, bidder == null ? null : bidder.toString());
            update.setString(3, id.toString());
            update.executeUpdate();
        }
    }

    public void delete(UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_auction_listings WHERE id = ?")) {
            delete.setString(1, id.toString());
            delete.executeUpdate();
        }
    }
}
