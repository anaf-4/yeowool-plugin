package com.yeowool.community.quest;

/** One entry from {@code config.yml}'s {@code daily.quest-pool}/{@code weekly.quest-pool} — a target delta against an existing statistic. */
public record QuestDefinition(String id, String statKey, long target, String display, long rewardOn, QuestDifficulty difficulty) {
}
