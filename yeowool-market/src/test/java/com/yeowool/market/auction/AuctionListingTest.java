package com.yeowool.market.auction;

import com.yeowool.core.api.model.CurrencyType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link AuctionListing}'s pure state checks and {@link
 * AuctionManager#minIncrement} — the math that decides whether a bid is
 * accepted and how much it must beat, ahead of real currency changing hands.
 * {@code item} is null throughout since none of these methods touch it.
 */
class AuctionListingTest {

    private AuctionListing listingWithoutBid(long startingBid, long buyNowPrice) {
        return new AuctionListing(UUID.randomUUID(), UUID.randomUUID(), null, startingBid, buyNowPrice,
                0, null, CurrencyType.ON, System.currentTimeMillis() + 60_000, System.currentTimeMillis());
    }

    @Test
    void hasBuyNowOnlyWhenPricePositive() {
        assertTrue(listingWithoutBid(1000, 5000).hasBuyNow());
        assertFalse(listingWithoutBid(1000, 0).hasBuyNow());
    }

    @Test
    void hasBidOnlyWhenBidderPresent() {
        AuctionListing noBid = listingWithoutBid(1000, 0);
        assertFalse(noBid.hasBid());

        AuctionListing withBid = noBid.withBid(UUID.randomUUID(), 1500);
        assertTrue(withBid.hasBid());
    }

    @Test
    void minNextBidIsStartingBidBeforeAnyBid() {
        AuctionListing listing = listingWithoutBid(1000, 0);
        assertEquals(1000, listing.minNextBid(50));
    }

    @Test
    void minNextBidIsCurrentBidPlusIncrementAfterABid() {
        AuctionListing listing = listingWithoutBid(1000, 0).withBid(UUID.randomUUID(), 2000);
        assertEquals(2050, listing.minNextBid(50));
    }

    @Test
    void minIncrementIsFivePercentOfStartingBidWithFloorOfOne() {
        AuctionManager manager = new AuctionManager(null, null, null, null);
        assertEquals(50, manager.minIncrement(listingWithoutBid(1000, 0)));
        assertEquals(1, manager.minIncrement(listingWithoutBid(1, 0)), "must never round down to 0");
        assertEquals(1, manager.minIncrement(listingWithoutBid(15, 0)));
    }
}
