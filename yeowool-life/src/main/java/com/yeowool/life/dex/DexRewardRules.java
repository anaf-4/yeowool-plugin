package com.yeowool.life.dex;

import java.util.List;

/** Dex completion math, kept free of Bukkit so it can be unit tested. */
public final class DexRewardRules {

    private DexRewardRules() {
    }

    /** floor(owned × 100 / total); an empty category is 0% so it never pays out. */
    public static int percent(int owned, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) ((long) Math.max(0, owned) * 100 / total);
    }

    /** First milestone above {@code percent} in an ascending list, or -1 when all are reached. */
    public static int nextMilestone(int percent, List<Integer> sortedMilestones) {
        for (int milestone : sortedMilestones) {
            if (percent < milestone) {
                return milestone;
            }
        }
        return -1;
    }

    /** Entries still needed so that {@link #percent} reaches {@code milestone}: ceil(milestone × total / 100) − owned, never negative. */
    public static int remainingFor(int milestone, int owned, int total) {
        long needed = ((long) milestone * total + 99) / 100;
        return (int) Math.max(0, needed - owned);
    }
}
