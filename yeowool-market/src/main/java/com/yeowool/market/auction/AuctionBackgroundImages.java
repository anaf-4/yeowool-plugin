package com.yeowool.market.auction;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Same font-image-in-the-title trick as every other module — renders the
 * {@code yeowool_auction:*_bg} backgrounds (converted from the "v0id
 * AuctionHouse 2.0" pack) behind {@link AuctionGui}/{@link BiddingGui}/
 * {@link ConfirmPurchaseGui}/{@link MyAuctionsGui}. Placeholder is the bare
 * font_image key, never "namespace:key".
 */
public final class AuctionBackgroundImages {

    private AuctionBackgroundImages() {
    }

    public static Component title(int offsetPx, String imageId, Component fallback) {
        if (imageId == null || imageId.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return fallback;
        }
        String offsetPlaceholder = ":offset_" + offsetPx + ":";
        String imagePlaceholder = ":" + imageId + ":";
        String legacy = FontImageWrapper.replaceFontImages(offsetPlaceholder + imagePlaceholder);
        if (legacy.contains(offsetPlaceholder) || legacy.contains(imagePlaceholder)) {
            return fallback;
        }
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    /** The pack's invisible {@code air.png} icon (every clickable button in the real plugin uses it — the graphics are baked into the background itself). */
    public static ItemStack invisibleIcon() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.getInstance("yeowool_auction:auction_invisible");
        return custom == null ? null : custom.getItemStack();
    }
}
