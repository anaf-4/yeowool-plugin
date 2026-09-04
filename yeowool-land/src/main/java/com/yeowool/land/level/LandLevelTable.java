package com.yeowool.land.level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data-driven land level thresholds, loaded from {@code config.yml}'s
 * {@code land-level:} list, per the plugin plan's requirement that the level
 * table be tunable without a code change. XP values here are placeholders —
 * tune them once real farming/logging/etc. XP rates exist in YeowoolLife.
 *
 * <p>Reaching a tier's XP only makes the upgrade *available* — the player
 * must run {@code /토지 업그레이드} to actually apply it (and pay
 * {@link Tier#cost()} from the land's own bank, if set), so a costed tier
 * acts as an "온" sink rather than an automatic level-up.
 */
public final class LandLevelTable {

    public record Tier(int level, long requiredXp, int maxChunks, long cost, long reward) {
    }

    private final JavaPlugin plugin;
    private List<Tier> tiers = List.of(new Tier(1, 0L, 1, 0L, 0L));

    public LandLevelTable(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        List<Map<?, ?>> rawList = config.getMapList("land-level");
        List<Tier> parsed = new ArrayList<>();
        for (Map<?, ?> entry : rawList) {
            try {
                int level = ((Number) entry.get("level")).intValue();
                long xp = ((Number) entry.get("xp")).longValue();
                int chunks = ((Number) entry.get("chunks")).intValue();
                long cost = entry.containsKey("cost") ? ((Number) entry.get("cost")).longValue() : 0L;
                long reward = entry.containsKey("reward") ? ((Number) entry.get("reward")).longValue() : 0L;
                parsed.add(new Tier(level, xp, chunks, cost, reward));
            } catch (Exception e) {
                plugin.getLogger().warning("land-level 설정 항목이 잘못되었습니다: " + entry);
            }
        }
        if (parsed.isEmpty()) {
            plugin.getLogger().warning("land-level 설정이 비어 있어 기본값(Lv.1 = 1청크)을 사용합니다.");
            parsed.add(new Tier(1, 0L, 1, 0L, 0L));
        }
        parsed.sort(Comparator.comparingInt(Tier::level));
        this.tiers = List.copyOf(parsed);
    }

    public int getMaxChunks(int currentLevel) {
        int max = tiers.get(0).maxChunks();
        for (Tier tier : tiers) {
            if (tier.level() <= currentLevel) {
                max = tier.maxChunks();
            }
        }
        return max;
    }

    /**
     * Highest level whose XP requirement {@code totalXp} satisfies — this is
     * "eligible to upgrade to", not "already at" (see class javadoc).
     */
    public int computeLevelForXp(long totalXp) {
        int level = tiers.get(0).level();
        for (Tier tier : tiers) {
            if (tier.requiredXp() <= totalXp) {
                level = tier.level();
            }
        }
        return level;
    }

    /** The next defined tier above {@code currentLevel}, if any. */
    public Optional<Tier> nextTier(int currentLevel) {
        return tiers.stream().filter(tier -> tier.level() > currentLevel).findFirst();
    }
}
