package com.yeowool.community.menu;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Same font-image-in-the-title trick as every other module — see {@code BattlePassBackgroundImages}/{@code QuestBackgroundImages}. */
public final class MenuBackgroundImages {

    private MenuBackgroundImages() {
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

    /** {@code yeowool_menu:menu_air} - fully transparent - so a button's slot lets the background art show through. */
    public static ItemStack transparentIcon() {
        ItemStack stack = icon("yeowool_menu:menu_air");
        return stack == null ? new ItemStack(org.bukkit.Material.PAPER) : stack.clone();
    }
}
