package com.yeowool.core.util;

/**
 * Shared "milliseconds to Korean human text" formatting, e.g.
 * {@code 1일 3시간 20분}. Used anywhere a countdown needs to be shown to a
 * player (timed items, crop/sapling growth remaining time, ...).
 */
public final class DurationFormat {

    private DurationFormat() {
    }

    public static String humanize(long ms) {
        long totalSeconds = Math.max(0, ms) / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) builder.append(days).append("일 ");
        if (hours > 0) builder.append(hours).append("시간 ");
        if (minutes > 0) builder.append(minutes).append("분 ");
        if (seconds > 0 || builder.isEmpty()) builder.append(seconds).append("초");
        return builder.toString().trim();
    }
}
