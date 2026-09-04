package com.yeowool.core.api.model;

/**
 * Which balance a price/reward/voucher is denominated in: the regular
 * gameplay currency ("온", earned through normal play) or the premium
 * currency ("캐시", intended for real-money purchases). Kept as a shared
 * core type so admin vouchers, NPC shop items, and reward configs across
 * every Yeowool plugin all speak the same two values.
 */
public enum CurrencyType {
    ON("온"),
    CASH("캐시");

    private final String displayName;

    CurrencyType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
