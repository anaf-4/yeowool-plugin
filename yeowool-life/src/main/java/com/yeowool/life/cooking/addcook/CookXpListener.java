package com.yeowool.life.cooking.addcook;

import com.github.teamhungry22.addcook.api.event.CookCompleteEvent;
import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Grants land XP for finishing a dish, the same way harvesting/logging/fishing already do. */
public final class CookXpListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerCook;

    public CookXpListener(YeowoolCoreAPI core, long xpPerCook) {
        this.core = core;
        this.xpPerCook = xpPerCook;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCookComplete(CookCompleteEvent event) {
        var player = event.getPlayer();
        core.landStats().addLandXp(player.getUniqueId(), xpPerCook);
        core.playerData().getIfLoaded(player.getUniqueId())
                .ifPresent(data -> data.addStatistic("life.cooking.cooked", 1));
    }
}
