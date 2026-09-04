package com.yeowool.community.battlepass;

/** The two parallel reward tracks every tier has — FREE unlocks for everyone, PREMIUM requires purchasing with 캐시. */
public enum BattlePassTrack {
    FREE("무료", "free"),
    PREMIUM("프리미엄", "premium");

    private final String label;
    private final String key;

    BattlePassTrack(String label, String key) {
        this.label = label;
        this.key = key;
    }

    public String label() {
        return label;
    }

    /** DB row key - stable even if the enum's display label changes later. */
    public String key() {
        return key;
    }
}
