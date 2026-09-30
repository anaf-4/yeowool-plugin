package com.yeowool.life.metals;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@code metals.yml} parsed, plus the pure rules on top of it (drop roll, weighted metal pick with the depth bonus,
 * smelt batch size/cost) — no Bukkit server access, so it's unit-tested directly.
 */
public record MetalConfig(boolean enabled, Set<String> worlds, double stoneChance, double oreChance, Set<Material> stoneBlocks,
                          int deepY, double deepMultiplier, boolean announceLegendary, Map<Tier, TierInfo> tiers,
                          int rawPerIngot, int maxBatch, List<Conversion> conversions, List<Recipe> recipes,
                          Map<String, Metal> metals) {

    public static final String OVERWORLD = "overworld";
    public static final String NETHER = "nether";
    /** Pack item suffixes (bundle_metals:&lt;id&gt;&lt;suffix&gt;). */
    public static final int INGOT = 4;
    public static final int RAW = 6;

    public enum Tier {
        COMMON, RARE, EPIC, LEGENDARY;

        String key() {
            return name().toLowerCase();
        }

        static Optional<Tier> byKey(String key) {
            for (Tier tier : values()) {
                if (tier.key().equalsIgnoreCase(key)) {
                    return Optional.of(tier);
                }
            }
            return Optional.empty();
        }
    }

    /** {@code color}: a MiniMessage color name. */
    public record TierInfo(String name, String color, int weight, long smeltCost, long xp) {
    }

    public record Metal(String id, String name, Tier tier, Set<String> dimensions) {
    }

    /** {@code ingots} pack ingots ↔ 1 of {@code bundle_metals:<id><suffix>}. */
    public record Conversion(String key, String name, int suffix, int ingots) {
    }

    /** A 대장간 craft: {@code boosterPercent} > 0 = 강화 촉진제, {@code charm} = 파괴 방지 부적. */
    public record Recipe(String key, String name, int boosterPercent, boolean charm, long money, Map<Tier, Integer> ingots) {
    }

    public static MetalConfig load(ConfigurationSection root, Logger logger) {
        Map<Tier, TierInfo> tiers = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            String path = "tiers." + tier.key() + ".";
            tiers.put(tier, new TierInfo(root.getString(path + "name", tier.key()), root.getString(path + "color", "white"),
                    Math.max(0, root.getInt(path + "weight", 0)), Math.max(0, root.getLong(path + "smelt-cost", 0)),
                    Math.max(0, root.getLong(path + "xp", 0))));
        }
        Set<Material> stoneBlocks = EnumSet.noneOf(Material.class);
        for (String name : root.getStringList("drop.stone-blocks")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                logger.warning("metals.yml drop.stone-blocks의 '" + name + "'는 블록 이름이 아닙니다 — 건너뜁니다.");
            } else {
                stoneBlocks.add(material);
            }
        }
        List<Conversion> conversions = new ArrayList<>();
        ConfigurationSection conversionSection = root.getConfigurationSection("conversions");
        if (conversionSection != null) {
            for (String key : conversionSection.getKeys(false)) {
                int suffix = conversionSection.getInt(key + ".suffix", 0);
                int ingots = conversionSection.getInt(key + ".ingots", 0);
                if (suffix < 1 || suffix > 7 || suffix == INGOT || ingots < 1) {
                    logger.warning("metals.yml conversions." + key + "의 suffix(1~7, 4 제외)/ingots(1 이상)가 잘못되었습니다 — 건너뜁니다.");
                    continue;
                }
                conversions.add(new Conversion(key, conversionSection.getString(key + ".name", key), suffix, ingots));
            }
        }
        List<Recipe> recipes = new ArrayList<>();
        ConfigurationSection craftSection = root.getConfigurationSection("crafting");
        if (craftSection != null) {
            for (String key : craftSection.getKeys(false)) {
                Map<Tier, Integer> ingots = new EnumMap<>(Tier.class);
                ConfigurationSection ingotSection = craftSection.getConfigurationSection(key + ".ingots");
                if (ingotSection != null) {
                    for (String tierKey : ingotSection.getKeys(false)) {
                        Tier.byKey(tierKey).ifPresentOrElse(tier -> ingots.put(tier, Math.max(0, ingotSection.getInt(tierKey))),
                                () -> logger.warning("metals.yml crafting." + key + ".ingots의 '" + tierKey + "'는 등급이 아닙니다."));
                    }
                }
                ingots.values().removeIf(count -> count == 0);
                int percent = craftSection.getInt(key + ".booster-percent", 0);
                boolean charm = craftSection.getBoolean(key + ".charm", false);
                if ((percent <= 0) == !charm || ingots.isEmpty()) {
                    logger.warning("metals.yml crafting." + key + "는 booster-percent(>0)와 charm 중 하나만, 주괴 재료와 함께 있어야 합니다 — 건너뜁니다.");
                    continue;
                }
                recipes.add(new Recipe(key, craftSection.getString(key + ".name", key), charm ? 0 : percent, charm,
                        Math.max(0, craftSection.getLong(key + ".money", 0)), ingots));
            }
        }
        Map<String, Metal> metals = new LinkedHashMap<>();
        ConfigurationSection metalSection = root.getConfigurationSection("metals");
        if (metalSection != null) {
            for (String id : metalSection.getKeys(false)) {
                Optional<Tier> tier = Tier.byKey(metalSection.getString(id + ".tier", ""));
                if (tier.isEmpty()) {
                    logger.warning("metals.yml metals." + id + ".tier가 common/rare/epic/legendary가 아닙니다 — 건너뜁니다.");
                    continue;
                }
                Set<String> dimensions = new LinkedHashSet<>();
                for (String dimension : metalSection.getStringList(id + ".dimensions")) {
                    dimensions.add(dimension.toLowerCase());
                }
                metals.put(id, new Metal(id, metalSection.getString(id + ".name", id), tier.get(), Set.copyOf(dimensions)));
            }
        }
        return new MetalConfig(root.getBoolean("enabled", true), Set.copyOf(root.getStringList("worlds")),
                root.getDouble("drop.stone-chance-percent", 0.4), root.getDouble("drop.ore-chance-percent", 2.0), stoneBlocks,
                root.getInt("drop.deep-y", 0), root.getDouble("drop.deep-multiplier", 2.0), root.getBoolean("drop.announce-legendary", true),
                tiers, Math.max(1, root.getInt("smelting.raw-per-ingot", 4)), Math.max(1, root.getInt("smelting.max-batch", 64)),
                List.copyOf(conversions), List.copyOf(recipes), metals);
    }

    public TierInfo tier(Tier tier) {
        return tiers.get(tier);
    }

    // ---- drops ----

    /** True with probability {@code percent}/100. */
    public static boolean rollDrop(Random random, double percent) {
        return random.nextDouble() * 100 < percent;
    }

    /**
     * A metal found in {@code dimension} at height {@code y}: a tier by weight among the tiers that have a metal there
     * (영웅·전설 weight × {@link #deepMultiplier} at or below {@link #deepY}), then a uniform metal of that tier.
     */
    public Optional<Metal> pick(Random random, String dimension, int y) {
        Map<Tier, List<Metal>> pool = new EnumMap<>(Tier.class);
        for (Metal metal : metals.values()) {
            if (metal.dimensions().contains(dimension)) {
                pool.computeIfAbsent(metal.tier(), tier -> new ArrayList<>()).add(metal);
            }
        }
        Map<Tier, Double> weights = new EnumMap<>(Tier.class);
        for (Tier tier : pool.keySet()) {
            weights.put(tier, weight(tier, y));
        }
        return pickTier(random, weights).map(tier -> {
            List<Metal> candidates = pool.get(tier);
            return candidates.get(random.nextInt(candidates.size()));
        });
    }

    /** The tier's drop weight at height {@code y}, with the depth bonus for 영웅·전설. */
    public double weight(Tier tier, int y) {
        double weight = tiers.get(tier).weight();
        return (tier == Tier.EPIC || tier == Tier.LEGENDARY) && y <= deepY ? weight * deepMultiplier : weight;
    }

    static Optional<Tier> pickTier(Random random, Map<Tier, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        double roll = random.nextDouble() * total;
        Tier last = null;
        for (Map.Entry<Tier, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            last = entry.getKey();
            roll -= entry.getValue();
            if (roll < 0) {
                return Optional.of(last);
            }
        }
        return Optional.ofNullable(last); // floating-point leftover
    }

    // ---- smelting ----

    /** How many ingots one click smelts: limited by raw ores, balance, the batch cap ({@code wanted}, ≤ max-batch). */
    public int smeltTimes(int rawOwned, long balance, Tier tier, int wanted) {
        long cost = tiers.get(tier).smeltCost();
        long byMoney = cost == 0 ? Long.MAX_VALUE : balance / cost;
        return (int) Math.max(0, Math.min(Math.min(rawOwned / rawPerIngot, byMoney), Math.min(wanted, maxBatch)));
    }

    public long smeltCost(Tier tier, int times) {
        return Math.multiplyExact(tiers.get(tier).smeltCost(), (long) times);
    }
}
