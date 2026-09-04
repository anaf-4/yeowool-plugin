package com.yeowool.life.fishing;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Renders {@link FishCatalogGui}'s title as the "fishing_expansion" ItemsAdder
 * pack's {@code fish_codex} full-inventory background image — same
 * font-image-in-the-title trick {@code JobBackgroundImages}/{@code
 * ShopBackgroundImages} already use, duplicated here rather than shared for
 * the same reason those two are. Falls back to plain text if ItemsAdder isn't
 * installed or the image id doesn't resolve. The pixel offset is
 * pack/image-specific and generally needs a small visual nudge in-game — see
 * {@code fishing.gui-background-offset}.
 */
public final class FishBackgroundImages {

    private FishBackgroundImages() {
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
