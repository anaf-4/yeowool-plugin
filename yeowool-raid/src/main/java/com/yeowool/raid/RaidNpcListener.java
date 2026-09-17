package com.yeowool.raid;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Mirrors QuestNpcListener exactly: a right-clicked NPC opens the list of raids whose npcId matches it. */
public final class RaidNpcListener implements Listener {

    private final RaidManager raidManager;
    private final RaidEntryService entryService;

    public RaidNpcListener(RaidManager raidManager, RaidEntryService entryService) {
        this.raidManager = raidManager;
        this.entryService = entryService;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        var raids = raidManager.byNpc(event.getNPC().getId());
        if (raids.isEmpty()) {
            return;
        }
        Player player = event.getClicker();
        new RaidListGui(raids, raid -> entryService.attemptEntry(player, raid)).open(player);
    }
}
