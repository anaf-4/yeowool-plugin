package com.yeowool.market.npcshop;

/**
 * Per-shop restriction an admin picks in {@code config.yml}, on top of each
 * {@link ShopItem}'s own buy/sell prices — e.g. a "수집소" shop can be set
 * to {@code SELL_ONLY} so it buys crops but never sells anything back,
 * regardless of what prices happen to be configured on individual items.
 */
public enum ShopMode {
    BUY_ONLY,
    SELL_ONLY,
    BOTH;

    public boolean allowsBuying() {
        return this != SELL_ONLY;
    }

    public boolean allowsSelling() {
        return this != BUY_ONLY;
    }
}
