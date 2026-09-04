package com.yeowool.market.auction;

import com.yeowool.core.api.model.CurrencyType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One live auction (plugin plan 7.4, built as a standalone follow-up rather
 * than the initial "not implemented" scope). Bidding is escrowed: a bidder's
 * balance is withdrawn the moment they bid and refunded if outbid, so a
 * winner can never fail to pay (see {@link AuctionManager#bid}).
 */
public record AuctionListing(
        UUID id,
        UUID seller,
        ItemStack item,
        long startingBid,
        long buyNowPrice,
        long currentBid,
        UUID currentBidder,
        CurrencyType currency,
        long endAtMillis,
        long createdAt
) {
    public boolean hasBuyNow() {
        return buyNowPrice > 0;
    }

    public boolean hasBid() {
        return currentBidder != null;
    }

    public long minNextBid(long increment) {
        return hasBid() ? currentBid + increment : startingBid;
    }

    public AuctionListing withBid(UUID bidder, long amount) {
        return new AuctionListing(id, seller, item, startingBid, buyNowPrice, amount, bidder, currency, endAtMillis, createdAt);
    }
}
