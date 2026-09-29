package com.yeowool.life.fishing.orders;

import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Difficulty;
import com.yeowool.life.orders.OrderRules.Draw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Pure 어부 주문 rules (no Bukkit): CustomFishing weight → difficulty, size conditions, the size bonus and
 * star multiplier, and the delivery order (smallest first, star variants last).
 */
public final class FishingOrderRules {

    public static final String BIG = "BIG";
    public static final String GOLDEN = "GOLDEN";

    /** Weight thresholds: ≥ {@code easyMin} 쉬움, ≥ {@code normalMin} 보통, ≥ {@code legendaryBelow} 어려움, else 전설 (VIP only). */
    public record Settings(double normalMin, double easyMin, double legendaryBelow, int conditionChancePercent,
                           double conditionMultiplier, double sizeBonusMax, int bigTopPercent, Set<String> exclude) {
    }

    /**
     * One species: {@code id} is the order id ({@code yw_} stripped), {@code lootId} its base CustomFishing loot.
     * {@code minCm}/{@code maxCm} 0 = no size range known.
     */
    public record Species(String id, String lootId, double weight, double minCm, double maxCm, boolean hasGolden) {
        public boolean hasSize() {
            return maxCm > minCm && minCm >= 0;
        }
    }

    /** One fish in an inventory: star grade ({@link OrderRules#NORMAL}/SILVER/GOLDEN) and size in mm (null = unreadable). */
    public record Fish(int star, Integer sizeMm) {
    }

    /** Delivery order: 일반 → 은별 → 금별, and within each the smallest first (unknown size first). */
    public static final Comparator<Fish> DELIVERY_ORDER = Comparator.comparingInt(Fish::star)
            .thenComparingInt(fish -> fish.sizeMm() == null ? -1 : fish.sizeMm());

    private final Settings settings;
    private final OrderRules rules;

    public FishingOrderRules(Settings settings, OrderRules rules) {
        this.settings = settings;
        this.rules = rules;
    }

    public Settings settings() {
        return settings;
    }

    /** Natural difficulty from the CustomFishing weight; empty = 전설. */
    public Optional<Difficulty> grade(double weight) {
        if (weight >= settings.easyMin()) {
            return Optional.of(Difficulty.EASY);
        }
        if (weight >= settings.normalMin()) {
            return Optional.of(Difficulty.NORMAL);
        }
        return weight >= settings.legendaryBelow() ? Optional.of(Difficulty.HARD) : Optional.empty();
    }

    /** 전설 stay VIP-only unless an override gives them a difficulty; 희귀·전설 with a size range or 금별 may be VIP. */
    public Candidate candidate(Species species) {
        Optional<Difficulty> natural = grade(species.weight());
        OrderRules.Override override = rules.settings().overrides().get(species.id());
        boolean vipOnly = natural.isEmpty() && (override == null || override.difficulty() == null);
        Difficulty difficulty = rules.difficulty(species.id(), natural.orElse(Difficulty.HARD));
        boolean vipEligible = (vipOnly || difficulty == Difficulty.HARD) && (species.hasSize() || species.hasGolden());
        return new Candidate(species.id(), difficulty, vipEligible, vipOnly);
    }

    /** Minimum size of a size-conditioned order: the middle of the range, in mm. */
    public static int conditionMm(Species species) {
        return (int) Math.round((species.minCm() + species.maxCm()) / 2 * 10);
    }

    /** Minimum size of a 대어 VIP order: the top {@code bigTopPercent} of the range, in mm. */
    public int bigMm(Species species) {
        return (int) Math.round((species.maxCm() - (species.maxCm() - species.minCm()) * settings.bigTopPercent() / 100.0) * 10);
    }

    /** Adds the size condition (a {@code conditionChancePercent} of normal orders) or the VIP kind (대어 / 금별). */
    public Draw decorate(Random random, Draw draw, Species species) {
        if (species == null) {
            return draw;
        }
        if (draw.vip()) {
            List<String> kinds = new ArrayList<>();
            if (species.hasSize()) {
                kinds.add(BIG);
            }
            if (species.hasGolden()) {
                kinds.add(GOLDEN);
            }
            if (kinds.isEmpty()) {
                return draw;
            }
            String kind = kinds.size() == 1 ? kinds.get(0) : kinds.get(random.nextInt(kinds.size()));
            return draw.withConditions(kind, kind.equals(BIG) ? bigMm(species) : 0);
        }
        if (species.hasSize() && random.nextInt(100) < settings.conditionChancePercent()) {
            return draw.withConditions(null, conditionMm(species));
        }
        return draw;
    }

    /** Whether {@code fish} of the order's species may go into {@code order}. */
    public static boolean matches(Order order, Fish fish) {
        if (GOLDEN.equals(order.vipKind()) && fish.star() != OrderRules.GOLDEN) {
            return false;
        }
        return order.minSizeMm() <= 0 || fish.sizeMm() != null && fish.sizeMm() >= order.minSizeMm();
    }

    /** ×1 … ×(1 + sizeBonusMax) by where the size sits in the species' range; ×1 without size info. */
    public double sizeBonus(Integer sizeMm, Species species) {
        if (sizeMm == null || species == null || !species.hasSize()) {
            return 1;
        }
        double position = (sizeMm / 10.0 - species.minCm()) / (species.maxCm() - species.minCm());
        return 1 + settings.sizeBonusMax() * Math.max(0, Math.min(1, position));
    }

    /** One fish's reward multiplier: group = star only; personal = size bonus × star × size-condition multiplier. */
    public double multiplier(Order order, Fish fish, Species species, boolean group) {
        double star = rules.settings().itemMultipliers().get(fish.star());
        if (group) {
            return star;
        }
        return sizeBonus(fish.sizeMm(), species) * star * (order.minSizeMm() > 0 ? settings.conditionMultiplier() : 1);
    }
}
