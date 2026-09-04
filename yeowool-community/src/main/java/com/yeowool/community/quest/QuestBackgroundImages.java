package com.yeowool.community.quest;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Same font-image-in-the-title trick as every other module — renders the
 * {@code daily_quest:daily_quest_bg} background (converted from the
 * DailyQuest-1.8.3 pack's daily_quest.png) behind {@link QuestBoardGui},
 * {@link QuestLeaderboardGui}, and {@link QuestBadgesGui}. Placeholder is the bare font_image key, never
 * "namespace:key". {@link #icon} resolves the pack's icon items with a
 * material fallback, same pattern as {@code PlayerWarpIcons}.
 */
public final class QuestBackgroundImages {

    private QuestBackgroundImages() {
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
}
