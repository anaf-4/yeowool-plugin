package com.yeowool.federation;

/** Level-up and member-cap formulas, read from config.yml's level-up / member-cap sections. Pure — unit-tested. */
public record FederationLevelConfig(long activityPerLevel, long costPerLevel, int memberBase, int memberPerLevel) {

    /** Cumulative activity needed to go from {@code level} to {@code level + 1}. */
    public long activityForNextLevel(int level) {
        return activityPerLevel * level;
    }

    /** Bank cost (deducted) to go from {@code level} to {@code level + 1}. */
    public long costForNextLevel(int level) {
        return costPerLevel * level;
    }

    /** How many lands (leader's included) a federation of this level may hold. */
    public int memberCap(int level) {
        return memberBase + memberPerLevel * (level - 1);
    }
}
