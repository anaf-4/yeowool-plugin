package com.yeowool.enhance;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static balance settings from {@code config.yml} — tiers, success rates,
 * fail-risk thresholds, stat bonuses. Per-level 온/재료 cost is deliberately
 * NOT here; that lives in {@link EnhanceCostManager} (DB-backed, in-game
 * editable via {@code /강화설정}).
 */
public final class EnhanceConfig {

    private final int maxLevel;
    private final List<EnhanceTier> tiers;
    private final double defaultSuccessRate;
    private final Map<Integer, Double> successRateOverrides;
    private final int failDestroyStartLevel;
    private final double failDestroyChancePercent;
    private final double failDowngradeChancePercent;
    private final String protectionItemId;
    private final double weaponAttackDamagePerLevel;
    private final double armorArmorPerLevel;

    private EnhanceConfig(int maxLevel, List<EnhanceTier> tiers, double defaultSuccessRate,
                           Map<Integer, Double> successRateOverrides, int failDestroyStartLevel,
                           double failDestroyChancePercent, double failDowngradeChancePercent, String protectionItemId,
                           double weaponAttackDamagePerLevel, double armorArmorPerLevel) {
        this.maxLevel = maxLevel;
        this.tiers = tiers;
        this.defaultSuccessRate = defaultSuccessRate;
        this.successRateOverrides = successRateOverrides;
        this.failDestroyStartLevel = failDestroyStartLevel;
        this.failDestroyChancePercent = failDestroyChancePercent;
        this.failDowngradeChancePercent = failDowngradeChancePercent;
        this.protectionItemId = protectionItemId;
        this.weaponAttackDamagePerLevel = weaponAttackDamagePerLevel;
        this.armorArmorPerLevel = armorArmorPerLevel;
    }

    public static EnhanceConfig load(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("enhance");
        if (root == null) {
            throw new IllegalStateException("config.yml에 enhance 섹션이 없습니다.");
        }

        List<EnhanceTier> tiers = new ArrayList<>();
        for (Map<?, ?> raw : root.getMapList("tiers")) {
            int level = ((Number) raw.get("level")).intValue();
            String name = String.valueOf(raw.get("name"));
            NamedTextColor color = NamedTextColor.NAMES.value(String.valueOf(raw.get("color")).toLowerCase());
            if (color == null) {
                color = NamedTextColor.WHITE;
            }
            double statMultiplier = ((Number) raw.get("stat-multiplier")).doubleValue();
            tiers.add(new EnhanceTier(level, name, color, statMultiplier));
        }
        tiers.sort(Comparator.comparingInt(EnhanceTier::level));
        if (tiers.isEmpty()) {
            tiers.add(new EnhanceTier(0, "일반", NamedTextColor.WHITE, 1.0));
        }

        Map<Integer, Double> overrides = new HashMap<>();
        ConfigurationSection perLevel = root.getConfigurationSection("success-rate.per-level");
        if (perLevel != null) {
            for (String key : perLevel.getKeys(false)) {
                try {
                    overrides.put(Integer.parseInt(key), perLevel.getDouble(key));
                } catch (NumberFormatException ignored) {
                    // skip malformed key
                }
            }
        }

        return new EnhanceConfig(
                root.getInt("max-level", 30),
                tiers,
                root.getDouble("success-rate.default", 90.0),
                overrides,
                root.getInt("fail-destroy-start-level", 15),
                root.getDouble("fail-destroy-chance-percent", 15.0),
                root.getDouble("fail-downgrade-chance-percent", 35.0),
                root.getString("protection-item", "NETHERITE_INGOT"),
                root.getDouble("weapon-attack-damage-per-level", 0.4),
                root.getDouble("armor-armor-per-level", 0.25)
        );
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** Sorted ascending by threshold level — index position drives {@link EnhanceItemData}'s tooltip style escalation. */
    public List<EnhanceTier> tiers() {
        return List.copyOf(tiers);
    }

    /** The highest tier whose level threshold is {@code <=} the given level. */
    public EnhanceTier tierFor(int level) {
        EnhanceTier current = tiers.get(0);
        for (EnhanceTier tier : tiers) {
            if (tier.level() <= level) {
                current = tier;
            } else {
                break;
            }
        }
        return current;
    }

    /** {@code true} if enhancing from {@code fromLevel} to {@code fromLevel + 1} crosses into a new tier. */
    public boolean crossesTierAt(int fromLevel) {
        return tiers.stream().anyMatch(tier -> tier.level() == fromLevel + 1);
    }

    public double successRate(int fromLevel) {
        return successRateOverrides.getOrDefault(fromLevel, defaultSuccessRate);
    }

    public boolean isFailRisky(int fromLevel) {
        return fromLevel >= failDestroyStartLevel;
    }

    public double failDestroyChancePercent() {
        return failDestroyChancePercent;
    }

    public double failDowngradeChancePercent() {
        return failDowngradeChancePercent;
    }

    /** Vanilla Material name or an ItemsAdder namespaced id — see {@link EnhanceMaterialResolver}. Null/blank disables the protection mechanic. */
    public String protectionItemId() {
        return protectionItemId;
    }

    public double weaponAttackDamagePerLevel() {
        return weaponAttackDamagePerLevel;
    }

    public double armorArmorPerLevel() {
        return armorArmorPerLevel;
    }
}
