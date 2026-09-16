package com.yeowool.quest;

/** What {@code /퀘스트수락} actually has to track progress on. */
public enum QuestObjectiveType {
    /** Dialogue only — accepting immediately completes it and grants the reward. */
    NONE,
    /** Kill {@code objectiveAmount} of a vanilla {@link org.bukkit.entity.EntityType}. */
    KILL,
    /** Pick up {@code objectiveAmount} of a {@link org.bukkit.Material}. */
    COLLECT
}
