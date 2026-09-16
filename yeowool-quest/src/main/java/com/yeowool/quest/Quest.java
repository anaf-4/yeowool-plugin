package com.yeowool.quest;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One admin-defined quest, bound to a single Citizens NPC (by id). A single
 * NPC can offer several quests — {@code npcId} isn't unique.
 */
public record Quest(long id, String name, int npcId, List<String> dialogue, QuestObjectiveType objectiveType,
                     String objectiveTarget, int objectiveAmount, List<ItemStack> rewardItems, long createdAt) {

    public Quest withDialogue(List<String> newDialogue) {
        return new Quest(id, name, npcId, newDialogue, objectiveType, objectiveTarget, objectiveAmount, rewardItems, createdAt);
    }

    public Quest withObjective(QuestObjectiveType newType, String newTarget, int newAmount) {
        return new Quest(id, name, npcId, dialogue, newType, newTarget, newAmount, rewardItems, createdAt);
    }

    public Quest withRewardItems(List<ItemStack> newItems) {
        return new Quest(id, name, npcId, dialogue, objectiveType, objectiveTarget, objectiveAmount, newItems, createdAt);
    }
}
