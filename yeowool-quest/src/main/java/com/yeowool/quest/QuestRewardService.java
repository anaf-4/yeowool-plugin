package com.yeowool.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class QuestRewardService {

    private QuestRewardService() {
    }

    static void complete(Player player, QuestManager questManager, Quest quest) {
        questManager.save(new QuestProgress(player.getUniqueId(), quest.id(), QuestState.COMPLETED, quest.objectiveAmount(), 0));
        ItemStack[] clones = quest.rewardItems().stream().map(ItemStack::clone).toArray(ItemStack[]::new);
        var leftover = player.getInventory().addItem(clones);
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        player.sendMessage(Component.text("퀘스트 완료: ", NamedTextColor.GOLD).append(Component.text(quest.name(), NamedTextColor.YELLOW)));
    }
}
