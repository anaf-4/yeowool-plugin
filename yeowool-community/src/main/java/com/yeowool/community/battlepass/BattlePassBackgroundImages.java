package com.yeowool.community.battlepass;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Same font-image-in-the-title trick as every other module — see {@code QuestBackgroundImages}. */
public final class BattlePassBackgroundImages {

    private BattlePassBackgroundImages() {
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

    public static ItemStack icon(String customIconId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.getInstance(customIconId);
        return custom == null ? null : custom.getItemStack();
    }

    /**
     * A small growing bar of {@code bp_progress_bar} tiles (2px each) for a lore line, built the
     * same way {@link #title} builds a full background - just many repeats of one glyph instead
     * of one. Returns {@code null} (render nothing) rather than a fallback when ItemsAdder/the
     * glyph isn't available, since a missing progress bar is fine to just omit from the lore.
     */
    public static Component progressBar(int filledSegments) {
        if (filledSegments <= 0 || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        String placeholder = ":bp_progress_bar:";
        String raw = placeholder.repeat(filledSegments);
        String legacy = FontImageWrapper.replaceFontImages(raw);
        if (legacy.contains(placeholder)) {
            return null;
        }
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}
