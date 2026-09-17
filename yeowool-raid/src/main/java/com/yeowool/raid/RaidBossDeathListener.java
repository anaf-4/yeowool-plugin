package com.yeowool.raid;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class RaidBossDeathListener implements Listener {

    private final RaidManager raidManager;
    private final YeowoolCoreAPI core;
    private final RaidHudService hudService;

    public RaidBossDeathListener(RaidManager raidManager, YeowoolCoreAPI core, RaidHudService hudService) {
        this.raidManager = raidManager;
        this.core = core;
        this.hudService = hudService;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        var session = raidManager.sessionByBossEntity(event.getEntity().getUniqueId());
        if (session.isEmpty() || session.get().getState() != RaidSessionState.IN_PROGRESS) {
            return;
        }
        var raid = raidManager.all().stream().filter(r -> r.id() == session.get().getRaidId()).findFirst();
        if (raid.isEmpty()) {
            return;
        }
        hudService.notifyResult(session.get(), true);
        RaidRewardService.completeVictory(raidManager, session.get(), raid.get(), core);
    }
}
