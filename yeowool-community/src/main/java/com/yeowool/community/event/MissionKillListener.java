package com.yeowool.community.event;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Feeds every player-caused mob kill into {@link MissionEventManager} for {@code KILL}-type missions. */
public final class MissionKillListener implements Listener {

    private final MissionEventManager missionManager;

    public MissionKillListener(MissionEventManager missionManager) {
        this.missionManager = missionManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!missionManager.isActive()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        missionManager.increment(killer, MissionEventManager.Kind.KILL, event.getEntityType().name());
    }
}
