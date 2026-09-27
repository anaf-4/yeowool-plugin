package com.yeowool.life.surprise;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Pure decisions for surprise events, kept free of Bukkit so they can be unit tested. */
public final class SurpriseEventRules {

    private SurpriseEventRules() {
    }

    /** Random enabled type, avoiding {@code previous} when another is available; empty when none are enabled. */
    public static Optional<SurpriseEventType> pickType(Random random, List<SurpriseEventType> enabled, SurpriseEventType previous) {
        if (enabled.isEmpty()) {
            return Optional.empty();
        }
        List<SurpriseEventType> choices = enabled.stream().filter(type -> type != previous).toList();
        if (choices.isEmpty()) {
            choices = enabled;
        }
        return Optional.of(choices.get(random.nextInt(choices.size())));
    }

    /** Whole minutes in [min, max]; an inverted range collapses to min, negatives to 0. */
    public static long nextDelayMillis(Random random, int minMinutes, int maxMinutes) {
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        return (min + (long) random.nextInt(max - min + 1)) * 60_000L;
    }


    public static String formatMultiplier(double multiplier) {
        return multiplier == Math.rint(multiplier) ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
    }
}
