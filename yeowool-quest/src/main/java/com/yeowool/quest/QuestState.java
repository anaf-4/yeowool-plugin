package com.yeowool.quest;

/** One player's relationship to one {@link Quest}, stored in {@code yw_quest_progress}. No row at all means "never started". */
public enum QuestState {
    /** Mid-conversation with the NPC, stepping through dialogue lines. */
    DIALOGUE,
    /** Accepted — {@link QuestObjectiveType#KILL}/{@link QuestObjectiveType#COLLECT} progress is being tracked. */
    ACCEPTED,
    /** Reward already granted — the NPC won't offer this quest again. */
    COMPLETED
}
