package com.yeowool.quest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /퀘스트대사다음 <이름>} — meant to be run by BetterHud's dialogue-popup "SHIFT ▷ 계속" input, or typed directly. */
public final class QuestNextLineCommand implements CommandExecutor {

    private final QuestManager questManager;
    private final QuestDialogueService dialogueService;

    public QuestNextLineCommand(QuestManager questManager, QuestDialogueService dialogueService) {
        this.questManager = questManager;
        this.dialogueService = dialogueService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§c사용법: /퀘스트대사다음 <퀘스트이름>");
            return true;
        }
        var quest = questManager.find(args[0]);
        if (quest.isEmpty()) {
            sender.sendMessage("§c존재하지 않는 퀘스트입니다: " + args[0]);
            return true;
        }
        dialogueService.advance(player, quest.get());
        return true;
    }
}
