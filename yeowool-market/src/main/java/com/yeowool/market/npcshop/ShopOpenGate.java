package com.yeowool.market.npcshop;

import com.yeowool.core.api.event.ShopOpenEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Every shop ENTRY point asks this first; another plugin cancelling {@link ShopOpenEvent} keeps the shop closed. */
public final class ShopOpenGate {

    private ShopOpenGate() {
    }

    public static boolean allows(Player player, String shopId) {
        ShopOpenEvent event = new ShopOpenEvent(player, shopId);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
}
