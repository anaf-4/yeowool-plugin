package com.yeowool.community.quest;

/** One rung of the {@code quest.badges} ladder — unlocked once total quests completed reaches {@code threshold}. */
public record QuestBadge(String name, long threshold, String iconId) {
}
