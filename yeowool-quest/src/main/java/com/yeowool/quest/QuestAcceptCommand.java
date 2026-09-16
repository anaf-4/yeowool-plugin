package com.yeowool.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /퀘스트수락 <이름>} — meant to be run by BetterHud's decision-popup "수락" button, or typed directly. */
public final class QuestAcceptCommand implements CommandExecutor {

    private final QuestManager questManager;

    public QuestAcceptCommand(QuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§c사용법: /퀘스트수락 <퀘스트이름>");
            return true;
        }
        var quest = questManager.find(args[0]);
        if (quest.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + args[0]);
            return true;
        }
        if (quest.get().objectiveType() == QuestObjectiveType.NONE) {
            QuestRewardService.complete(player, questManager, quest.get());
            return true;
        }
        questManager.save(new QuestProgress(player.getUniqueId(), quest.get().id(), QuestState.ACCEPTED, 0, 0));
        player.sendMessage(Component.text("퀘스트를 수락했습니다: ", NamedTextColor.GREEN).append(Component.text(quest.get().name(), NamedTextColor.YELLOW)));
        return true;
    }
}
