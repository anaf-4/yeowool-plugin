package com.yeowool.enhance;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final double toolMiningEfficiencyPerLevel;
    private final List<TranscendStage> transcendStages;
    private final boolean transcendProtectFromDestroy;

    private EnhanceConfig(int maxLevel, List<EnhanceTier> tiers, double defaultSuccessRate,
                           Map<Integer, Double> successRateOverrides, int failDestroyStartLevel,
                           double failDestroyChancePercent, double failDowngradeChancePercent, String protectionItemId,
                           double weaponAttackDamagePerLevel, double armorArmorPerLevel,
                           double toolMiningEfficiencyPerLevel, List<TranscendStage> transcendStages,
                           boolean transcendProtectFromDestroy) {
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
        this.toolMiningEfficiencyPerLevel = toolMiningEfficiencyPerLevel;
        this.transcendStages = transcendStages;
        this.transcendProtectFromDestroy = transcendProtectFromDestroy;
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

        List<TranscendStage> transcendStages = new ArrayList<>();
        for (Map<?, ?> raw : root.getMapList("transcend.stages")) {
            try {
                NamedTextColor stageColor = NamedTextColor.NAMES.value(String.valueOf(raw.get("color")).toLowerCase());
                transcendStages.add(new TranscendStage(
                        ((Number) raw.get("stage")).intValue(),
                        String.valueOf(raw.get("name")),
                        stageColor == null ? NamedTextColor.DARK_PURPLE : stageColor,
                        ((Number) raw.get("stat-multiplier")).doubleValue(),
                        String.valueOf(raw.get("stone-item")),
                        raw.get("stone-amount") instanceof Number n ? n.intValue() : 1,
                        raw.get("currency") instanceof Number n ? n.longValue() : 0L,
                        raw.get("success-rate") instanceof Number n ? n.doubleValue() : 50.0,
                        raw.get("enhance-cost-multiplier") instanceof Number n ? n.doubleValue() : 1.0));
            } catch (RuntimeException e) {
                // skip a malformed stage entry
            }
        }
        transcendStages.sort(Comparator.comparingInt(TranscendStage::stage));

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
                root.getDouble("armor-armor-per-level", 0.25),
                root.getDouble("tool-mining-efficiency-per-level", 0.3),
                transcendStages,
                root.getBoolean("transcend.protect-from-destroy", true)
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

    public double toolMiningEfficiencyPerLevel() {
        return toolMiningEfficiencyPerLevel;
    }

    /** The configured stage {@code stage} (1..3), if any — a missing stage means transcendence stops before it. */
    public Optional<TranscendStage> transcendStage(int stage) {
        return transcendStages.stream().filter(s -> s.stage() == stage).findFirst();
    }

    /** Transcended gear is never destroyed by a failed enhance (the destroy roll becomes a downgrade). */
    public boolean transcendProtectFromDestroy() {
        return transcendProtectFromDestroy;
    }
}
