package com.yeowool.quest;

import java.util.UUID;

/** One player's progress on one {@link Quest}. */
public record QuestProgress(UUID uuid, long questId, QuestState state, int progress, int dialogueIndex) {

    public QuestProgress withState(QuestState newState) {
        return new QuestProgress(uuid, questId, newState, progress, dialogueIndex);
    }

    public QuestProgress withProgress(int newProgress) {
        return new QuestProgress(uuid, questId, state, newProgress, dialogueIndex);
    }

    public QuestProgress withDialogueIndex(int newIndex) {
        return new QuestProgress(uuid, questId, state, progress, newIndex);
    }
}
