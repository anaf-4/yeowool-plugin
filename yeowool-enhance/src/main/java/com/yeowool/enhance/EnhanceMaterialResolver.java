package com.yeowool.enhance;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Cost materials (magic ore etc.) and the protection item are stored as a
 * plain string id — either a vanilla {@link Material} name ("DIAMOND") or an
 * ItemsAdder namespaced id ("moafarm_items:magic_ore_1", detected by the
 * presence of ":"). Mirrors {@code yeowool-market}'s {@code ItemResolver}
 * but simplified to just "does the player have N of this" / "take N of this",
 * since enhance doesn't need to render shop icons for it.
 */
public final class EnhanceMaterialResolver {

    private EnhanceMaterialResolver() {
    }

    public static boolean isCustomItem(String id) {
        return id != null && id.contains(":");
    }

    public static String displayName(String id) {
        if (isCustomItem(id) && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(id);
            if (custom != null) {
                return custom.getDisplayName();
            }
        }
        return id;
    }

    public static boolean hasAmount(Player player, String id, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (isCustomItem(id)) {
            return countCustom(player, id) >= amount;
        }
        Material material = parseVanilla(id);
        return material != null && player.getInventory().containsAtLeast(new ItemStack(material), amount);
    }

    public static void removeAmount(Player player, String id, int amount) {
        if (amount <= 0) {
            return;
        }
        if (isCustomItem(id)) {
            removeCustom(player, id, amount);
            return;
        }
        Material material = parseVanilla(id);
        if (material != null) {
            player.getInventory().removeItem(new ItemStack(material, amount));
        }
    }

    private static int countCustom(Player player, String id) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (matchesCustom(stack, id)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void removeCustom(Player player, String id, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!matchesCustom(stack, id)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
        }
    }

    private static boolean matchesCustom(ItemStack stack, String id) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null && custom.getNamespacedID().equals(id);
    }

    private static Material parseVanilla(String id) {
        try {
            return Material.valueOf(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
