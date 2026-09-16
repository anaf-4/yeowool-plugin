package com.yeowool.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

/** {@link QuestObjectiveType#COLLECT} — counts cumulative pickups of a vanilla {@link org.bukkit.Material} (no turn-in step). */
public final class QuestCollectListener implements Listener {

    private final QuestManager questManager;

    public QuestCollectListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        String material = event.getItem().getItemStack().getType().name();
        int amount = event.getItem().getItemStack().getAmount();
        for (Quest quest : questManager.all()) {
            if (quest.objectiveType() == QuestObjectiveType.COLLECT && material.equalsIgnoreCase(quest.objectiveTarget())) {
                progressOne(player, quest, amount);
            }
        }
    }

    private void progressOne(Player player, Quest quest, int amount) {
        int newProgress = questManager.incrementObjective(player.getUniqueId(), quest, amount);
        if (newProgress < 0) {
            return;
        }
        if (newProgress >= quest.objectiveAmount()) {
            QuestRewardService.complete(player, questManager, quest);
        } else {
            player.sendMessage(Component.text("[" + quest.name() + "] " + newProgress + " / " + quest.objectiveAmount(), NamedTextColor.AQUA));
        }
    }
}
