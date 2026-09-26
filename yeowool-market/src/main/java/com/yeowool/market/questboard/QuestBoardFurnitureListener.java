package com.yeowool.market.questboard;

import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Right-clicking the configured ItemsAdder furniture (W6 Quest Board by
 * default) opens the board. Only registered when ItemsAdder is enabled —
 * this class must not be loaded otherwise.
 */
public final class QuestBoardFurnitureListener implements Listener {

    private final QuestBoardService service;

    public QuestBoardFurnitureListener(QuestBoardService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        if (!service.settings().furnitureId().equals(event.getNamespacedID())) {
            return;
        }
        event.setCancelled(true);
        service.openBoard(event.getPlayer(), 0);
    }
}
