package com.yeowool.quest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class QuestJoinListener implements Listener {

    private final QuestManager questManager;

    public QuestJoinListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        questManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        questManager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
