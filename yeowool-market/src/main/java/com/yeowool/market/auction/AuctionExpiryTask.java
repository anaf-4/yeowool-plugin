package com.yeowool.market.auction;

import org.bukkit.scheduler.BukkitRunnable;

/** Periodically settles any auction whose timer has run out. */
public final class AuctionExpiryTask extends BukkitRunnable {

    private final AuctionManager manager;
    private final double feePercent;

    public AuctionExpiryTask(AuctionManager manager, double feePercent) {
        this.manager = manager;
        this.feePercent = feePercent;
    }

    @Override
    public void run() {
        for (AuctionListing listing : manager.expired(System.currentTimeMillis())) {
            manager.settle(listing, feePercent);
        }
    }
}
