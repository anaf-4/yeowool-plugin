package com.yeowool.raid;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs once a minute; ends any session whose raid.timeLimitSeconds() has elapsed as a loss. */
public final class RaidTimeoutTask implements Runnable {

    private final RaidManager raidManager;
    private final RaidHudService hudService;

    public RaidTimeoutTask(RaidManager raidManager, RaidHudService hudService) {
        this.raidManager = raidManager;
        this.hudService = hudService;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L * 60, 20L * 60);
    }

    @Override
    public void run() {
        for (RaidSession session : java.util.List.copyOf(raidManager.activeSessions())) {
            if (session.getState() != RaidSessionState.IN_PROGRESS) {
                continue;
            }
            var raid = raidManager.all().stream().filter(r -> r.id() == session.getRaidId()).findFirst();
            if (raid.isEmpty() || !session.isExpired(raid.get().timeLimitSeconds())) {
                continue;
            }
            hudService.notifyResult(session, false);
            raidManager.endSession(session, RaidSessionState.LOST);
        }
    }
}
