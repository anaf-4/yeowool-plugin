package com.yeowool.land.villageevent;

import com.yeowool.core.api.event.PlayerLandXpChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Feeds every land-XP gain into {@link VillageEventManager}, which itself no-ops unless an event is running. */
public final class VillageEventXpListener implements Listener {

    private final VillageEventManager eventManager;

    public VillageEventXpListener(VillageEventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onLandXpChange(PlayerLandXpChangeEvent event) {
        eventManager.accumulate(event.getUuid(), event.getDelta());
    }
}
