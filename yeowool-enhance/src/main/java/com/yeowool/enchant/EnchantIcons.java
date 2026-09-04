package com.yeowool.enchant;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Shared ItemsAdder icon-resolve helper for the {@code /인챈트강화} screens. */
public final class EnchantIcons {

    private EnchantIcons() {
    }

    public static ItemStack resolveCustom(String customIconId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.getInstance(customIconId);
        return custom == null ? null : custom.getItemStack();
    }
}
