package com.yeowool.admin.coupon;

import org.bukkit.inventory.ItemStack;

/**
 * One redeemable code, keyed by {@code code} lowercased (see
 * {@link CouponManager}). Players redeem it by typing the exact text into an
 * anvil's rename field (see {@link CouponRedeemListener}) — no crafting
 * click needed, matching in-game "코드 입력" UX.
 */
public record Coupon(String code, ItemStack rewardItem, long createdAt, long expiresAt) {

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public Coupon withRewardItem(ItemStack newItem) {
        return new Coupon(code, newItem, createdAt, expiresAt);
    }

    public Coupon withExpiresAt(long newExpiresAt) {
        return new Coupon(code, rewardItem, createdAt, newExpiresAt);
    }
}
