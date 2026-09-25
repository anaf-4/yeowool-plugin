package com.yeowool.federation.shop;

import com.yeowool.core.api.event.ShopOpenEvent;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.OptionalInt;

/** Vetoes entering a federation shop unless the player's (cached) federation level is high enough. */
public final class FederationShopGate implements Listener {

    private final MessageService messages;
    private final List<FederationShop> shops;
    private final FederationLevelCache levelCache;

    public FederationShopGate(MessageService messages, List<FederationShop> shops, FederationLevelCache levelCache) {
        this.messages = messages;
        this.shops = shops;
        this.levelCache = levelCache;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopOpen(ShopOpenEvent event) {
        FederationShop shop = shops.stream()
                .filter(candidate -> candidate.shopId().equals(event.getShopId()))
                .findFirst()
                .orElse(null);
        if (shop == null) {
            return;
        }
        OptionalInt level = levelCache.get(event.getPlayer().getUniqueId());
        if (level.isEmpty()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "federation.shop-loading");
            return;
        }
        if (level.getAsInt() < shop.minLevel()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "federation.shop-locked",
                    Placeholder.unparsed("level", String.valueOf(shop.minLevel())));
        }
    }
}
