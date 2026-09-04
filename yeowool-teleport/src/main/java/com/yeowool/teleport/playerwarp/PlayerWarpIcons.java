package com.yeowool.teleport.playerwarp;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Shared icon-building helpers for every {@code playerwarp} GUI — one place for the "custom id, falls back to material" pattern used throughout. */
public final class PlayerWarpIcons {

    private PlayerWarpIcons() {
    }

    public static ItemStack resolveCustom(String customIconId) {
        if (customIconId == null || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.getInstance(customIconId);
        return custom == null ? null : custom.getItemStack();
    }

    public static ItemStack icon(String customIconId, Material fallback, String name, NamedTextColor color) {
        ItemStack stack = resolveCustom(customIconId);
        if (stack == null) {
            stack = new ItemStack(fallback);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack icon(String customIconId, Material fallback, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = icon(customIconId, fallback, name, color);
        ItemMeta meta = stack.getItemMeta();
        List<Component> plainLore = lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();
        meta.lore(plainLore);
        stack.setItemMeta(meta);
        return stack;
    }
}
