package com.yeowool.admin.coupon;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One redeemable code, keyed by {@code code} lowercased (see
 * {@link CouponManager}). Players redeem it by typing the exact text into an
 * anvil's rename field (see {@link CouponRedeemListener}) — no crafting
 * click needed, matching in-game "코드 입력" UX. {@code rewardItems} can hold
 * more than one stack — see {@link CouponCreateCommand}.
 */
public record Coupon(String code, List<ItemStack> rewardItems, long createdAt, long expiresAt) {

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public Coupon withRewardItems(List<ItemStack> newItems) {
        return new Coupon(code, newItems, createdAt, expiresAt);
    }

    public Coupon withExpiresAt(long newExpiresAt) {
        return new Coupon(code, rewardItems, createdAt, newExpiresAt);
    }
}
