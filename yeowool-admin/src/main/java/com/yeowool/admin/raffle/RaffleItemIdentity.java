package com.yeowool.admin.raffle;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** What "같은 아이템"/"다른 아이템" means for {@link RaffleManager}'s weight tracking. */
public final class RaffleItemIdentity {

    private RaffleItemIdentity() {
    }

    /** Vanilla material name, or the ItemsAdder namespaced id if this is a custom item. */
    public static String identify(ItemStack item) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.byItemStack(item);
            if (custom != null) {
                return custom.getNamespacedID();
            }
        }
        return item.getType().name();
    }

    public static String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.byItemStack(item);
            if (custom != null) {
                return custom.getDisplayName();
            }
        }
        return item.getType().name();
    }
}
