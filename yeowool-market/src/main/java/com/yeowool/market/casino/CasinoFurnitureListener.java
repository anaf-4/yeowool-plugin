package com.yeowool.market.casino;

import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Right-clicking a casino table furniture opens its game. Only registered when ItemsAdder is enabled. */
public final class CasinoFurnitureListener implements Listener {

    private final CasinoService service;

    public CasinoFurnitureListener(CasinoService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        CasinoService.Game game = service.settings().furniture().get(event.getNamespacedID());
        if (game == null) {
            return; // plain decoration
        }
        event.setCancelled(true);
        service.openGame(event.getPlayer(), game);
    }
}
