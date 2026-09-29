package com.yeowool.life.orders;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Pure rules shared by 요리 주문 and 어부 주문 (no Bukkit): drawing the day's orders, reward math,
 * fame levels and the group-order schedule. Everything tunable comes in through {@link Settings};
 * what an item is (recipe / fish species) comes in as a {@link Candidate} from the system's catalog.
 */
public final class OrderRules {

    public enum Difficulty {
        EASY("easy", "쉬움"), NORMAL("normal", "보통"), HARD("hard", "어려움");

        private final String key;
        private final String label;

        Difficulty(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public static Optional<Difficulty> byKey(String key) {
            for (Difficulty difficulty : values()) {
                if (difficulty.key.equalsIgnoreCase(key)) {
                    return Optional.of(difficulty);
                }
            }
            return Optional.empty();
        }
    }

    /** Item grade = index into {@link Settings#itemMultipliers()} (dish quality / fish star). */
    public static final int NORMAL = 0;
    public static final int SILVER = 1;
    public static final int GOLDEN = 2;

    public record Tier(int amountMin, int amountMax, long moneyPerItem, long stardust, int fame, long jobXp) {
    }

    public record Vip(int chancePercent, int amountMin, int amountMax, double moneyMultiplier, long stardust, int fame, long jobXp) {
    }

    public record Group(List<DayOfWeek> days, LocalTime startTime, int durationHours, int easyTarget, int normalTarget,
                        double moneyShare, int minContribution, long participationStardust, List<Long> rankStardust) {
    }

    public record FameLevel(String name, int fame, double multiplier) {
    }

    /** {@code difficulty} null / {@code moneyPerItem} ≤ 0 = not overridden. */
    public record Override(Difficulty difficulty, long moneyPerItem) {
    }

    /** {@code itemMultipliers} indexed by {@link #NORMAL}/{@link #SILVER}/{@link #GOLDEN}; {@code fameLevels} ascending by fame. */
    public record Settings(int ordersPerDay, Map<Difficulty, Tier> tiers, List<Double> itemMultipliers,
                           long allDoneMoney, long allDoneStardust, long rerollCost, long stardustDailyCap, Vip vip, Group group,
                           List<FameLevel> fameLevels, Map<String, List<String>> levelUpCommands,
                           Map<String, Override> overrides) {
    }

    /**
     * One item a player may be offered. {@code difficulty} already has overrides applied; {@code vipOnly}
     * items (legendary fish) never show up as normal orders.
     */
    public record Candidate(String id, Difficulty difficulty, boolean vipEligible, boolean vipOnly) {
    }

    /** {@code vipKind} (null = the system has one kind) and {@code minSizeMm} (0 = none) are catalog conditions. */
    public record Draw(String itemId, Difficulty difficulty, boolean vip, int required, String vipKind, int minSizeMm) {
        public Draw withConditions(String vipKind, int minSizeMm) {
            return new Draw(itemId, difficulty, vip, required, vipKind, minSizeMm);
        }
    }

    private final Settings settings;

    public OrderRules(Settings settings) {
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    /** {@code natural} unless the config overrides this item's difficulty. */
    public Difficulty difficulty(String id, Difficulty natural) {
        Override override = settings.overrides().get(id);
        return override != null && override.difficulty() != null ? override.difficulty() : natural;
    }

    public Tier tier(Difficulty difficulty) {
        return settings.tiers().get(difficulty);
    }

    public long moneyPerItem(String id, Difficulty difficulty) {
        Override override = settings.overrides().get(id);
        return override != null && override.moneyPerItem() > 0 ? override.moneyPerItem() : tier(difficulty).moneyPerItem();
    }

    /**
     * The day's orders from {@code offered}: distinct unless fewer are offered than {@code ordersPerDay}.
     * With {@code vip.chancePercent} one of them is a VIP order for a VIP-eligible item. Empty if nothing
     * but VIP-only items is offered.
     */
    public List<Draw> draw(Random random, List<Candidate> offered) {
        List<Candidate> pool = offered.stream().filter(candidate -> !candidate.vipOnly()).toList();
        if (pool.isEmpty()) {
            return List.of();
        }
        List<Candidate> eligible = offered.stream().filter(Candidate::vipEligible).toList();
        Candidate vip = !eligible.isEmpty() && random.nextInt(100) < settings.vip().chancePercent()
                ? eligible.get(random.nextInt(eligible.size())) : null;
        Set<String> used = new HashSet<>();
        if (vip != null) {
            used.add(vip.id());
        }
        List<Draw> orders = new ArrayList<>();
        int normalCount = settings.ordersPerDay() - (vip == null ? 0 : 1);
        for (int i = 0; i < normalCount; i++) {
            orders.add(normalOrder(random, pick(random, pool, used)));
        }
        if (vip != null) {
            orders.add(random.nextInt(orders.size() + 1),
                    new Draw(vip.id(), vip.difficulty(), true, roll(random, settings.vip().amountMin(), settings.vip().amountMax()), null, 0));
        }
        return orders;
    }

    /** A fresh non-VIP order for 교체, avoiding {@code exclude} (today's other items) when possible. */
    public Optional<Draw> drawReplacement(Random random, List<Candidate> offered, Set<String> exclude) {
        List<Candidate> pool = offered.stream().filter(candidate -> !candidate.vipOnly()).toList();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(normalOrder(random, pick(random, pool, new HashSet<>(exclude))));
    }

    private Draw normalOrder(Random random, Candidate candidate) {
        Tier tier = tier(candidate.difficulty());
        return new Draw(candidate.id(), candidate.difficulty(), false, roll(random, tier.amountMin(), tier.amountMax()), null, 0);
    }

    private static Candidate pick(Random random, List<Candidate> pool, Set<String> used) {
        List<Candidate> fresh = pool.stream().filter(candidate -> !used.contains(candidate.id())).toList();
        List<Candidate> from = fresh.isEmpty() ? pool : fresh;
        Candidate candidate = from.get(random.nextInt(from.size()));
        used.add(candidate.id());
        return candidate;
    }

    static int roll(Random random, int min, int max) {
        int low = Math.max(1, min);
        return max <= low ? low : low + random.nextInt(max - low + 1);
    }

    /** 온 for one delivered item: base × item multiplier × fame, or base × VIP multiplier × fame for VIP. */
    public long itemMoney(long base, double itemMultiplier, boolean vip, double fameMultiplier) {
        double multiplier = vip ? settings.vip().moneyMultiplier() : itemMultiplier;
        return Math.round(base * multiplier * fameMultiplier);
    }

    /** 온 for one item delivered to the group order: half (money-share) of base × item multiplier, no fame bonus. */
    public long groupItemMoney(long base, double itemMultiplier) {
        return Math.round(base * settings.group().moneyShare() * itemMultiplier);
    }

    public FameLevel level(int fame) {
        FameLevel current = settings.fameLevels().get(0);
        for (FameLevel level : settings.fameLevels()) {
            if (fame >= level.fame()) {
                current = level;
            }
        }
        return current;
    }

    public Optional<FameLevel> nextLevel(int fame) {
        return settings.fameLevels().stream().filter(level -> level.fame() > fame).findFirst();
    }

    public int groupTarget(Difficulty difficulty) {
        return difficulty == Difficulty.EASY ? settings.group().easyTarget() : settings.group().normalTarget();
    }

    /** A random 쉬움/보통 item for a group order (any item, offered to the player or not). */
    public Optional<Candidate> pickGroupItem(Random random, List<Candidate> all) {
        List<Candidate> pool = all.stream().filter(candidate -> !candidate.vipOnly() && candidate.difficulty() != Difficulty.HARD).toList();
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /** The scheduled group-order start whose window contains {@code now}, if any (the latest one). */
    public Optional<ZonedDateTime> currentGroupStart(ZonedDateTime now) {
        Group group = settings.group();
        LocalDate today = now.toLocalDate();
        for (int back = 0; back <= group.durationHours() / 24 + 1; back++) {
            LocalDate date = today.minusDays(back);
            if (!group.days().contains(date.getDayOfWeek())) {
                continue;
            }
            ZonedDateTime start = date.atTime(group.startTime()).atZone(now.getZone());
            if (!start.isAfter(now) && now.isBefore(start.plusHours(group.durationHours()))) {
                return Optional.of(start);
            }
        }
        return Optional.empty();
    }

    /** The next scheduled group-order start strictly after {@code now}. */
    public Optional<ZonedDateTime> nextGroupStart(ZonedDateTime now) {
        Group group = settings.group();
        for (int ahead = 0; ahead <= 7; ahead++) {
            LocalDate date = now.toLocalDate().plusDays(ahead);
            ZonedDateTime start = date.atTime(group.startTime()).atZone(now.getZone());
            if (group.days().contains(date.getDayOfWeek()) && start.isAfter(now)) {
                return Optional.of(start);
            }
        }
        return Optional.empty();
    }
}
