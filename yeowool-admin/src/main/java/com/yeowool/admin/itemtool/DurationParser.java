package com.yeowool.admin.itemtool;

import com.yeowool.core.util.DurationFormat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses admin-friendly duration strings for {@code /기간제 설정}, e.g.
 * {@code "30m"}, {@code "2h"}, {@code "1d12h"}, {@code "7d"}. A bare number
 * with no unit is treated as minutes.
 */
public final class DurationParser {

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)([smhd])");

    private DurationParser() {
    }

    /** Returns milliseconds, or -1 if the string couldn't be parsed. */
    public static long parseToMillis(String input) {
        String trimmed = input.trim().toLowerCase();
        if (trimmed.matches("\\d+")) {
            return Long.parseLong(trimmed) * 60_000L;
        }

        Matcher matcher = SEGMENT.matcher(trimmed);
        long totalMs = 0L;
        int matchedChars = 0;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            totalMs += switch (matcher.group(2)) {
                case "s" -> amount * 1_000L;
                case "m" -> amount * 60_000L;
                case "h" -> amount * 3_600_000L;
                case "d" -> amount * 86_400_000L;
                default -> 0L;
            };
            matchedChars += matcher.group().length();
        }

        return (matchedChars == trimmed.length() && totalMs > 0) ? totalMs : -1;
    }

    public static String humanize(long ms) {
        return DurationFormat.humanize(ms);
    }
}
