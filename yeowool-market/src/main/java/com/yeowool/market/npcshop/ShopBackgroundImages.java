package com.yeowool.market.npcshop;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Renders a GUI title as one of the "Spectra ShopGUI+" ItemsAdder pack's
 * full-inventory background images (same font-image-in-the-title trick
 * {@code AttendanceGui} uses for {@code /출석체크}) — falls back to plain
 * text if ItemsAdder isn't installed or the pack isn't imported. The pack's
 * own {@code ShopGUIPlus/shops/*.yml} bakes in an {@code :offset_-52:}
 * spacer glyph immediately before every background id (e.g. {@code name: '&f
 * :offset_-52: :shop_item_display:'}), but that number was tuned for their
 * plugin's own title rendering — ours needs its own value, so it's read
 * from {@code npc-shop.gui-background-offset} (config.yml) instead of
 * hardcoded, letting an admin nudge it left/right without a rebuild.
 */
public final class ShopBackgroundImages {

    private ShopBackgroundImages() {
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

    /** A small inline glyph (e.g. {@code icon_left_click}) for use inside item lore, or {@code fallback} if it fails to resolve. */
    public static String glyph(String imageId, String fallback) {
        if (imageId == null || imageId.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return fallback;
        }
        String placeholder = ":" + imageId + ":";
        String legacy = FontImageWrapper.replaceFontImages(placeholder);
        return legacy.contains(placeholder) ? fallback : legacy;
    }
}
