package com.yeowool.community.battlepass;

import com.yeowool.community.quest.QuestDifficulty;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/** Loads the {@code battlepass:} config.yml section. */
public final class BattlePassConfig {

    private final int season;
    private final long premiumPriceCash;
    private final int backgroundOffsetPx;
    private final int rewardsBackgroundOffsetPx;
    private final Map<QuestDifficulty, Long> pointsPerQuest;

    private BattlePassConfig(int season, long premiumPriceCash, int backgroundOffsetPx, int rewardsBackgroundOffsetPx,
                              Map<QuestDifficulty, Long> pointsPerQuest) {
        this.season = season;
        this.premiumPriceCash = premiumPriceCash;
        this.backgroundOffsetPx = backgroundOffsetPx;
        this.rewardsBackgroundOffsetPx = rewardsBackgroundOffsetPx;
        this.pointsPerQuest = pointsPerQuest;
    }

    public static BattlePassConfig load(FileConfiguration config) {
        Map<QuestDifficulty, Long> points = new EnumMap<>(QuestDifficulty.class);
        for (QuestDifficulty difficulty : QuestDifficulty.values()) {
            points.put(difficulty, config.getLong("battlepass.points-per-quest." + difficulty.name().toLowerCase(), 10));
        }
        return new BattlePassConfig(
                config.getInt("battlepass.season", 1),
                config.getLong("battlepass.premium-price-cash", 9900),
                config.getInt("battlepass.gui-background-offset", -8),
                config.getInt("battlepass.gui-background-offset-rewards", -8),
                points);
    }

    public int season() {
        return season;
    }

    public long premiumPriceCash() {
        return premiumPriceCash;
    }

    public int backgroundOffsetPx() {
        return backgroundOffsetPx;
    }

    /** Separate from {@link #backgroundOffsetPx()} so the rewards screen's background can be nudged left/right independently of the portal's. */
    public int rewardsBackgroundOffsetPx() {
        return rewardsBackgroundOffsetPx;
    }

    public long pointsFor(QuestDifficulty difficulty) {
        return pointsPerQuest.getOrDefault(difficulty, 10L);
    }
}
