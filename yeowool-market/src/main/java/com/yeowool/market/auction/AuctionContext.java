package com.yeowool.market.auction;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

/** Bundles every dependency the auction house GUI screens need, to avoid threading 7 loose params through the whole chain. */
public record AuctionContext(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, AuctionManager manager,
                              double feePercent, int backgroundOffsetPx, long[] bidIncrements) {
}
