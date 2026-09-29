package com.yeowool.life.cooking.orders;

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
 * Pure rules for 요리 주문 (no Bukkit): difficulty, drawing the day's orders, reward math,
 * fame levels and the group-order schedule. Everything tunable comes in through {@link Settings}.
 */
public final class CookingOrderRules {

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

    /** Dish quality = index into a recipe's result list. */
    public static final int NORMAL = 0;
    public static final int SILVER = 1;
    public static final int GOLDEN = 2;

    public record Tier(int amountMin, int amountMax, long moneyPerDish, long stardust, int fame) {
    }

    public record Vip(int chancePercent, int amountMin, int amountMax, double moneyMultiplier, long stardust, int fame) {
    }

    public record Group(List<DayOfWeek> days, LocalTime startTime, int durationHours, int easyTarget, int normalTarget,
                        double moneyShare, int minContribution, long participationStardust, List<Long> rankStardust) {
    }

    public record FameLevel(String name, int fame, double multiplier) {
    }

    /** {@code difficulty} null / {@code moneyPerDish} ≤ 0 = not overridden. */
    public record RecipeOverride(Difficulty difficulty, long moneyPerDish) {
    }

    /** {@code qualityMultipliers} indexed by {@link #NORMAL}/{@link #SILVER}/{@link #GOLDEN}; {@code fameLevels} ascending by fame. */
    public record Settings(int ordersPerDay, Map<Difficulty, Tier> tiers, List<Double> qualityMultipliers,
                           long allDoneMoney, long allDoneStardust, long rerollCost, long stardustDailyCap, Vip vip, Group group,
                           List<FameLevel> fameLevels, Map<String, List<String>> levelUpCommands,
                           Map<String, RecipeOverride> overrides) {
    }

    /** What the rules need to know about an AddCook recipe; {@code qualities} = number of result tiers (3 = has 금). */
    public record Recipe(String id, int stages, int qualities) {
        public boolean hasGolden() {
            return qualities > GOLDEN;
        }
    }

    public record Draw(String recipeId, Difficulty difficulty, boolean vip, int required) {
    }

    private final Settings settings;

    public CookingOrderRules(Settings settings) {
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    public static Difficulty byStages(int stages) {
        return stages <= 1 ? Difficulty.EASY : stages == 2 ? Difficulty.NORMAL : Difficulty.HARD;
    }

    public Difficulty difficulty(Recipe recipe) {
        RecipeOverride override = settings.overrides().get(recipe.id());
        return override != null && override.difficulty() != null ? override.difficulty() : byStages(recipe.stages());
    }

    public Tier tier(Difficulty difficulty) {
        return settings.tiers().get(difficulty);
    }

    public long moneyPerDish(String recipeId, Difficulty difficulty) {
        RecipeOverride override = settings.overrides().get(recipeId);
        return override != null && override.moneyPerDish() > 0 ? override.moneyPerDish() : tier(difficulty).moneyPerDish();
    }

    /**
     * The day's orders from the recipes the player has learned: distinct unless fewer are learned than
     * {@code ordersPerDay}. With {@code vip.chancePercent} one of them is a VIP order for a recipe that has a 금 tier.
     */
    public List<Draw> draw(Random random, List<Recipe> learned) {
        if (learned.isEmpty()) {
            return List.of();
        }
        List<Recipe> golden = learned.stream().filter(Recipe::hasGolden).toList();
        Recipe vip = !golden.isEmpty() && random.nextInt(100) < settings.vip().chancePercent()
                ? golden.get(random.nextInt(golden.size())) : null;
        Set<String> used = new HashSet<>();
        if (vip != null) {
            used.add(vip.id());
        }
        List<Draw> orders = new ArrayList<>();
        int normalCount = settings.ordersPerDay() - (vip == null ? 0 : 1);
        for (int i = 0; i < normalCount; i++) {
            orders.add(normalOrder(random, pick(random, learned, used)));
        }
        if (vip != null) {
            orders.add(random.nextInt(orders.size() + 1),
                    new Draw(vip.id(), difficulty(vip), true, roll(random, settings.vip().amountMin(), settings.vip().amountMax())));
        }
        return orders;
    }

    /** A fresh non-VIP order for 교체, avoiding {@code exclude} (today's other recipes) when possible. */
    public Optional<Draw> drawReplacement(Random random, List<Recipe> learned, Set<String> exclude) {
        if (learned.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(normalOrder(random, pick(random, learned, new HashSet<>(exclude))));
    }

    private Draw normalOrder(Random random, Recipe recipe) {
        Difficulty difficulty = difficulty(recipe);
        Tier tier = tier(difficulty);
        return new Draw(recipe.id(), difficulty, false, roll(random, tier.amountMin(), tier.amountMax()));
    }

    private static Recipe pick(Random random, List<Recipe> pool, Set<String> used) {
        List<Recipe> fresh = pool.stream().filter(recipe -> !used.contains(recipe.id())).toList();
        List<Recipe> from = fresh.isEmpty() ? pool : fresh;
        Recipe recipe = from.get(random.nextInt(from.size()));
        used.add(recipe.id());
        return recipe;
    }

    static int roll(Random random, int min, int max) {
        int low = Math.max(1, min);
        return max <= low ? low : low + random.nextInt(max - low + 1);
    }

    /** 온 for one delivered dish: base × quality multiplier × fame multiplier, or base × VIP multiplier × fame for VIP. */
    public long dishMoney(long base, int quality, boolean vip, double fameMultiplier) {
        double multiplier = vip ? settings.vip().moneyMultiplier() : settings.qualityMultipliers().get(quality);
        return Math.round(base * multiplier * fameMultiplier);
    }

    /** 온 for one dish delivered to the group order: half (money-share) of base × quality, no fame bonus. */
    public long groupDishMoney(long base, int quality) {
        return Math.round(base * settings.group().moneyShare() * settings.qualityMultipliers().get(quality));
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

    /** A random 쉬움/보통 recipe for a group order (any recipe, learned or not). */
    public Optional<Recipe> pickGroupRecipe(Random random, List<Recipe> all) {
        List<Recipe> pool = all.stream().filter(recipe -> difficulty(recipe) != Difficulty.HARD).toList();
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
