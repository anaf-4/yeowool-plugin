package com.yeowool.life.donation;

import com.yeowool.life.donation.DonationRepository.Contribution;
import com.yeowool.life.donation.DonationRepository.Goal;
import com.yeowool.life.surprise.SurpriseEventType;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

/** Pure decisions for 기부 프로젝트, kept free of Bukkit so they can be unit tested. */
public final class DonationRules {

    /** One wanted item: {@code itemKey} is a Material name or an ItemsAdder {@code namespace:id}; {@code display} may be null. */
    public record GoalSpec(String itemKey, byte[] display, int target, int points) {
    }

    /** A project to start: from the config pool or the staff reservation. {@code buff} null = rotate. */
    public record Candidate(String name, SurpriseEventType buff, List<GoalSpec> goals) {
    }

    public record Settings(DayOfWeek startDay, int durationDays, int buffHours, List<Candidate> pool,
                           List<SurpriseEventType> buffOrder, long moneyPerPoint, long minScore,
                           long participationStardust, List<Long> rankStardust, List<String> topCommands) {
    }

    /** Overall-progress announcements, highest first. */
    static final int[] MILESTONES = {90, 50};

    private DonationRules() {
    }

    /** Start of the week containing {@code now}: the latest {@code day} 00:00 at or before it. */
    public static ZonedDateTime periodStart(ZonedDateTime now, DayOfWeek day) {
        return now.toLocalDate().with(TemporalAdjusters.previousOrSame(day)).atStartOfDay(now.getZone());
    }

    public static ZonedDateTime nextPeriodStart(ZonedDateTime now, DayOfWeek day) {
        return periodStart(now, day).plusWeeks(1);
    }

    /** When a project started at {@code now} ends: this week's deadline, or {@code durationDays} from now if that already passed. */
    public static ZonedDateTime endFor(ZonedDateTime now, DayOfWeek day, int durationDays) {
        ZonedDateTime end = periodStart(now, day).plusDays(durationDays);
        return now.isBefore(end) ? end : now.plusDays(durationDays);
    }

    /** How many of {@code requested} still fit into a goal. */
    public static int clamp(int requested, int target, int progress) {
        return Math.max(0, Math.min(requested, target - progress));
    }

    /** Average completion of the goals, 0..100 (floored, so 100 only when everything is done). */
    public static int overallPercent(List<Goal> goals) {
        if (goals.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Goal goal : goals) {
            sum += goal.target() <= 0 ? 1.0 : Math.min(1.0, (double) goal.progress() / goal.target());
        }
        return (int) Math.floor(sum / goals.size() * 100 + 1e-9);
    }

    public static boolean allComplete(List<Goal> goals) {
        return !goals.isEmpty() && goals.stream().allMatch(goal -> goal.progress() >= goal.target());
    }

    /** The highest milestone {@code percent} has reached (none at 100 — that's the 달성 announcement). */
    public static OptionalInt milestone(int percent) {
        if (percent >= 100) {
            return OptionalInt.empty();
        }
        for (int milestone : MILESTONES) {
            if (percent >= milestone) {
                return OptionalInt.of(milestone);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * 별조각 per contributor ({@code ranked}: most points first): {@code participation} for at least
     * {@code minScore} points, plus {@code rankStardust[i]} for rank {@code i + 1}.
     */
    public static List<Long> rewards(List<Contribution> ranked, long minScore, long participation, List<Long> rankStardust) {
        List<Long> rewards = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Contribution contribution = ranked.get(i);
            long reward = contribution.points() > 0 && contribution.points() >= minScore ? participation : 0;
            if (i < rankStardust.size()) {
                reward += rankStardust.get(i);
            }
            rewards.add(reward);
        }
        return rewards;
    }

    /** A random pool entry, avoiding last week's {@code previousName} when another exists. */
    public static Optional<Candidate> pick(Random random, List<Candidate> pool, String previousName) {
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        List<Candidate> choices = pool.stream().filter(candidate -> !candidate.name().equals(previousName)).toList();
        if (choices.isEmpty()) {
            choices = pool;
        }
        return Optional.of(choices.get(random.nextInt(choices.size())));
    }

    /** The buff after {@code previous} in {@code order} (the first if none/unknown). */
    public static SurpriseEventType nextBuff(List<SurpriseEventType> order, SurpriseEventType previous) {
        if (order.isEmpty()) {
            return SurpriseEventType.LAND_XP;
        }
        int index = previous == null ? -1 : order.indexOf(previous);
        return order.get((index + 1) % order.size());
    }

    /** 보물지도 발견 3배, the rest 2배 (spec). */
    public static double buffMultiplier(SurpriseEventType type) {
        return type == SurpriseEventType.TREASURE_DROP ? 3.0 : 2.0;
    }

    /** "■■■■□□□□□□"-style bar of {@code width} cells. */
    public static String bar(int progress, int target, int width) {
        int filled = target <= 0 ? width : (int) Math.min(width, (long) progress * width / target);
        return "■".repeat(filled) + "□".repeat(width - filled);
    }
}
