package com.yeowool.market.trade;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TradeQuitListener implements Listener {

    private final TradeManager manager;

    public TradeQuitListener(TradeManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forceCancel(event.getPlayer().getUniqueId());
    }
}
