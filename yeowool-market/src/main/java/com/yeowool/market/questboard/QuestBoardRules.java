package com.yeowool.market.questboard;

/**
 * Money math for the quest board, kept free of Bukkit so it can be unit
 * tested. Every method that multiplies returns -1 instead of overflowing so
 * callers can reject absurd requests before any money moves.
 */
public final class QuestBoardRules {

    private QuestBoardRules() {
    }

    /** quantity × rewardPerItem, or -1 when it overflows a long. */
    public static long totalReward(int quantity, long rewardPerItem) {
        try {
            return Math.multiplyExact((long) quantity, rewardPerItem);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** floor(total × feePercent / 100), or -1 when it overflows a long. */
    public static long fee(long total, int feePercent) {
        if (feePercent <= 0) {
            return 0;
        }
        try {
            return Math.addExact(Math.multiplyExact(total / 100, (long) feePercent), (total % 100) * feePercent / 100);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** What registering takes from the requester up front (total reward + burned fee), or -1 on overflow. */
    public static long upfrontCost(int quantity, long rewardPerItem, int feePercent) {
        long total = totalReward(quantity, rewardPerItem);
        long fee = total < 0 ? -1 : fee(total, feePercent);
        if (fee < 0) {
            return -1;
        }
        try {
            return Math.addExact(total, fee);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** Reward still held for items nobody delivered — paid back on cancel/expiry (the fee is not refunded). */
    public static long refund(int quantity, int delivered, long rewardPerItem) {
        return (long) Math.max(0, quantity - delivered) * rewardPerItem;
    }

    /** How many items one delivery takes: never more than the request still needs or the player holds. */
    public static int deliverable(int remaining, int held) {
        return Math.max(0, Math.min(remaining, held));
    }

    public static boolean validQuantity(long quantity, int max) {
        return quantity >= 1 && quantity <= max;
    }
}
