package com.yeowool.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /퀘스트거절 <이름>} — forgets progress so the NPC offers it again fresh later. */
public final class QuestDeclineCommand implements CommandExecutor {

    private final QuestManager questManager;

    public QuestDeclineCommand(QuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§c사용법: /퀘스트거절 <퀘스트이름>");
            return true;
        }
        var quest = questManager.find(args[0]);
        if (quest.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + args[0]);
            return true;
        }
        questManager.reset(player.getUniqueId(), quest.get().id());
        player.sendMessage(Component.text("퀘스트를 거절했습니다: ", NamedTextColor.GRAY).append(Component.text(quest.get().name(), NamedTextColor.YELLOW)));
        return true;
    }
}
