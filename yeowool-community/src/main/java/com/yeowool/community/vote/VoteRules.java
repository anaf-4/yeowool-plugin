package com.yeowool.community.vote;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/** Pure rules behind 추천 보상 — no Bukkit/JDBC, see {@code VoteRulesTest}. */
final class VoteRules {

    /** Reward row id for "every counted vote"; milestones are their vote count (≥ 1). */
    static final int EVERY_VOTE = 0;

    private VoteRules() {
    }

    /** Lowercase lookup key for a vote-site username, or null if it can't be a Minecraft name. */
    static String nameKey(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        return name.matches("\\S{1,16}") ? name.toLowerCase(Locale.ROOT) : null;
    }

    /** Smallest milestone above {@code total}. */
    static OptionalInt nextMilestone(Collection<Integer> thresholds, int total) {
        return thresholds.stream().mapToInt(Integer::intValue).filter(t -> t > EVERY_VOTE && t > total).min();
    }

    /** Milestones reached going from {@code before} to {@code after} votes, ascending. */
    static List<Integer> crossed(Collection<Integer> thresholds, int before, int after) {
        return thresholds.stream().filter(t -> t > EVERY_VOTE && t > before && t <= after).sorted().toList();
    }

    /** [first day of the month, first day of the next month) as ISO dates — matches the {@code day} column. */
    static String[] monthRange(YearMonth month) {
        LocalDate start = month.atDay(1);
        return new String[]{start.toString(), start.plusMonths(1).toString()};
    }

    /** e.g. "5,000온 + 별조각 3 + 아이템 2개", or "없음". */
    static String summary(long on, long stardust, int itemCount) {
        StringBuilder text = new StringBuilder();
        if (on > 0) {
            text.append(String.format("%,d온", on));
        }
        if (stardust > 0) {
            text.append(text.isEmpty() ? "" : " + ").append("별조각 ").append(stardust);
        }
        if (itemCount > 0) {
            text.append(text.isEmpty() ? "" : " + ").append("아이템 ").append(itemCount).append("개");
        }
        return text.isEmpty() ? "없음" : text.toString();
    }
}
