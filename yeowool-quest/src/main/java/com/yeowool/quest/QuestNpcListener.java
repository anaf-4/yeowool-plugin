package com.yeowool.quest;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

/** Right-clicking a quest NPC offers whichever of its quests this player hasn't completed yet. */
public final class QuestNpcListener implements Listener {

    private final QuestManager questManager;
    private final QuestDialogueService dialogueService;

    public QuestNpcListener(QuestManager questManager, QuestDialogueService dialogueService) {
        this.questManager = questManager;
        this.dialogueService = dialogueService;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        List<Quest> available = questManager.byNpc(event.getNPC().getId()).stream()
                .filter(quest -> questManager.isAvailable(event.getClicker().getUniqueId(), quest))
                .toList();
        if (available.isEmpty()) {
            return;
        }
        Player player = event.getClicker();
        if (available.size() == 1) {
            dialogueService.start(player, available.get(0));
            return;
        }
        new QuestListGui(available, quest -> dialogueService.start(player, quest)).open(player);
    }
}
