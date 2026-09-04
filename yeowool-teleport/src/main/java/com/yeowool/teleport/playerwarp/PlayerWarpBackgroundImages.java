package com.yeowool.teleport.playerwarp;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Same font-image-in-the-title trick as the rest of the project — renders
 * the {@code playerwarps_gui:playerwarps} background (converted from the
 * "PlayerWarps GUI" pack) behind {@link PlayerWarpBrowseGui}. Placeholder is
 * the bare font_image key, never "namespace:key" (that's the item-id
 * convention, not this one).
 */
public final class PlayerWarpBackgroundImages {

    private PlayerWarpBackgroundImages() {
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
}
