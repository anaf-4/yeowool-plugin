package com.yeowool.life.surprise;

/**
 * YeowoolLife-internal boost multipliers (1.0 = normal) that the surprise event turns up for a
 * while. Written on the main thread, read by the treasure-map and job XP code.
 */
public final class LifeBoosts {

    private static volatile double treasureDrop = 1.0;
    private static volatile double jobXp = 1.0;

    private LifeBoosts() {
    }

    public static double treasureDropMultiplier() {
        return treasureDrop;
    }

    public static void setTreasureDropMultiplier(double multiplier) {
        treasureDrop = multiplier;
    }

    public static double jobXpMultiplier() {
        return jobXp;
    }

    public static void setJobXpMultiplier(double multiplier) {
        jobXp = multiplier;
    }
}
