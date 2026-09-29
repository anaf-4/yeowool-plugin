package com.yeowool.enhance;

import java.util.List;
import java.util.Set;

/**
 * Transcendence math, kept free of Bukkit so it can be unit tested. Stage 0 is "not transcended";
 * each stage caps how far enhancing can go until the next transcendence: 0 → max-level, 1 → 10,
 * 2 → 20, 3 → max-level (the final peak).
 */
public final class TranscendRules {

    public static final int MAX_STAGE = 3;

    private static final List<String> UPGRADABLE_PREFIXES = List.of(
            "WOODEN_", "STONE_", "IRON_", "GOLDEN_", "DIAMOND_", "LEATHER_", "CHAINMAIL_");
    private static final Set<String> NETHERITE_PIECES = Set.of(
            "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private TranscendRules() {
    }

    /** Highest enhance level reachable in {@code stage} before the next transcendence is required. */
    public static int levelCap(int stage, int maxLevel) {
        return switch (stage) {
            case 1 -> 10;
            case 2 -> 20;
            default -> maxLevel;
        };
    }

    /** True when the item sits at its stage's cap and another stage exists. */
    public static boolean canTranscend(int stage, int level, int maxLevel) {
        return stage < MAX_STAGE && level >= levelCap(stage, maxLevel);
    }

    /** The netherite material name for gear that has one (e.g. DIAMOND_SWORD → NETHERITE_SWORD), otherwise unchanged. */
    public static String netheriteVariant(String materialName) {
        for (String prefix : UPGRADABLE_PREFIXES) {
            if (materialName.startsWith(prefix)) {
                String piece = materialName.substring(prefix.length());
                if (NETHERITE_PIECES.contains(piece)) {
                    return "NETHERITE_" + piece;
                }
            }
        }
        return materialName;
    }

    /**
     * Before transcendence: {@code level × perLevel × tierMultiplier} (the original formula).
     * After: {@code (maxLevel + level) × perLevel × stageMultiplier} — the first transcendence keeps
     * the full pre-transcend levels as a base, so its multiplier just needs to exceed the top tier's.
     */
    public static double bonus(int stage, int level, int maxLevel, double perLevel, double tierMultiplier, double stageMultiplier) {
        if (stage <= 0) {
            return level * perLevel * tierMultiplier;
        }
        return (maxLevel + level) * perLevel * stageMultiplier;
    }
}
