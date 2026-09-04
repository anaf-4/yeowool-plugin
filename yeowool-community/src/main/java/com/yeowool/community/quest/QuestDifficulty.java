package com.yeowool.community.quest;

import net.kyori.adventure.text.format.NamedTextColor;

/** E/M/H quest tiers, matching the DailyQuest-1.8.3 pack's easy/medium/hard_quest icons. */
public enum QuestDifficulty {
    EASY("난이도: 쉬움", NamedTextColor.GREEN, "daily_quest:easy_quest"),
    MEDIUM("난이도: 보통", NamedTextColor.AQUA, "daily_quest:medium_quest"),
    HARD("난이도: 어려움", NamedTextColor.RED, "daily_quest:hard_quest");

    private final String label;
    private final NamedTextColor color;
    private final String iconId;

    QuestDifficulty(String label, NamedTextColor color, String iconId) {
        this.label = label;
        this.color = color;
        this.iconId = iconId;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    public String iconId() {
        return iconId;
    }

    public static QuestDifficulty parse(String raw, QuestDifficulty fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return QuestDifficulty.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
