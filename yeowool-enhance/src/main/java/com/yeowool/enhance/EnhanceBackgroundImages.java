package com.yeowool.enhance;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Same font-image-in-the-title trick as every other module — renders the
 * {@code yeowool_enhance:enhance_bg} background (converted from
 * AdvancedEnchantments UI's enchanter.png) behind {@link EnhanceGui}.
 * Placeholder is the bare font_image key, never "namespace:key".
 */
public final class EnhanceBackgroundImages {

    private EnhanceBackgroundImages() {
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
