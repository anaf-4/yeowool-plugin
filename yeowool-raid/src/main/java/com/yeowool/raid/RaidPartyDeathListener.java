package com.yeowool.raid;

import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class RaidPartyDeathListener implements org.bukkit.event.Listener {

    private final RaidManager raidManager;
    private final RaidHudService hudService;

    public RaidPartyDeathListener(RaidManager raidManager, RaidHudService hudService) {
        this.raidManager = raidManager;
        this.hudService = hudService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        var session = raidManager.activeSessionFor(event.getEntity().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        var slot = raidManager.instanceSlotsFor(session.get().getRaidId()).stream()
                .filter(s -> s.slotIndex() == session.get().getSlotIndex())
                .findFirst();
        if (slot.isEmpty() || !slot.get().contains(event.getEntity().getLocation())) {
            return;
        }
        int remaining = session.get().decrementLives();
        if (remaining <= 0) {
            hudService.notifyResult(session.get(), false);
            raidManager.endSession(session.get(), RaidSessionState.LOST);
        } else {
            hudService.notifyLifeLost(session.get(), remaining);
        }
    }
}
