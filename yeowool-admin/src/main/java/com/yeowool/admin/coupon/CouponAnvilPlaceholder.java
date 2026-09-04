package com.yeowool.admin.coupon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The disposable "type here" item {@code /쿠폰} seeds into a virtual anvil's
 * first slot so the rename field is actually typable with no real item on
 * hand. Tagged via PDC so {@link CouponRedeemListener} can recognize and
 * discard it on close instead of letting it leak into the player's
 * inventory the way a real anvil returns its contents.
 */
final class CouponAnvilPlaceholder {

    private CouponAnvilPlaceholder() {
    }

    static ItemStack create(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("여기에 쿠폰 이름을 입력하세요", NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    static boolean isPlaceholder(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "coupon_placeholder");
    }
}
