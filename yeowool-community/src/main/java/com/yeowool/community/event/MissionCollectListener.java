package com.yeowool.community.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

/**
 * Feeds every player item pickup into {@link MissionEventManager} for
 * {@code COLLECT}-type missions — counts by material regardless of source
 * (mined, looted, picked off the ground), matching the mission's "수집"
 * framing rather than a specific gathering method.
 */
public final class MissionCollectListener implements Listener {

    private final MissionEventManager missionManager;

    public MissionCollectListener(MissionEventManager missionManager) {
        this.missionManager = missionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!missionManager.isActive() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        String material = event.getItem().getItemStack().getType().name();
        int amount = event.getItem().getItemStack().getAmount();
        for (int i = 0; i < amount; i++) {
            missionManager.increment(player, MissionEventManager.Kind.COLLECT, material);
        }
    }
}
