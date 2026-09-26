package com.yeowool.market.merchant;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Pure scheduling picks for the wandering merchant, kept out of the service so they can be unit tested. */
public final class MerchantRules {

    private MerchantRules() {
    }

    /** Random whole-minute delay in [minMinutes, maxMinutes]; an inverted range collapses to minMinutes, negatives to 0. */
    public static long nextDelayMillis(Random random, int minMinutes, int maxMinutes) {
        int min = Math.max(0, minMinutes);
        int max = Math.max(min, maxMinutes);
        return (min + (long) random.nextInt(max - min + 1)) * 60_000L;
    }

    public static <T> Optional<T> pick(List<T> options, Random random) {
        return options.isEmpty() ? Optional.empty() : Optional.of(options.get(random.nextInt(options.size())));
    }
}
