package com.yeowool.life.competition;

import java.util.Locale;
import java.util.Optional;

public enum CompetitionActivity {
    FISHING("fishing", "낚시", "잡은 물고기", "마리"),
    MINING("mining", "채광", "캔 광석", "개"),
    HUNTING("hunting", "사냥", "처치한 몹", "마리"),
    FARMING("farming", "농사", "수확한 작물", "개");

    private final String key;
    private final String label;
    private final String scoreLabel;
    private final String unit;

    CompetitionActivity(String key, String label, String scoreLabel, String unit) {
        this.key = key;
        this.label = label;
        this.scoreLabel = scoreLabel;
        this.unit = unit;
    }

    /** Same tag {@code PlayerRepeatableActionEvent} uses for mining/fishing/hunting, and the config key. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String scoreLabel() {
        return scoreLabel;
    }

    public String unit() {
        return unit;
    }

    public static Optional<CompetitionActivity> byKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (CompetitionActivity activity : values()) {
            if (activity.key.equals(normalized)) {
                return Optional.of(activity);
            }
        }
        return Optional.empty();
    }
}
