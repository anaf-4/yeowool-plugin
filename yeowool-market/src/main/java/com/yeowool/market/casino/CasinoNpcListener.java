package com.yeowool.market.casino;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Citizens-only: right-clicking a bound NPC opens the chip exchange. */
public final class CasinoNpcListener implements Listener {

    private final CasinoService service;

    public CasinoNpcListener(CasinoService service) {
        this.service = service;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (service.isCasinoNpc(event.getNPC().getId())) {
            service.openExchange(event.getClicker());
        }
    }
}
