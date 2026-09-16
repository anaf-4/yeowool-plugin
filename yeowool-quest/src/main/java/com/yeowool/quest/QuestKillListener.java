package com.yeowool.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** {@link QuestObjectiveType#KILL} — vanilla {@link org.bukkit.entity.EntityType} only (no MythicMobs mob-id matching in this first cut). */
public final class QuestKillListener implements Listener {

    private final QuestManager questManager;

    public QuestKillListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String entityType = event.getEntity().getType().name();
        for (Quest quest : questManager.all()) {
            if (quest.objectiveType() == QuestObjectiveType.KILL && entityType.equalsIgnoreCase(quest.objectiveTarget())) {
                progressOne(killer, quest);
            }
        }
    }

    private void progressOne(Player player, Quest quest) {
        int newProgress = questManager.incrementObjective(player.getUniqueId(), quest, 1);
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
