package com.yeowool.community.nickname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The disposable "type here" item seeded into the virtual anvil
 * {@link NicknameVoucherListener} opens on right-click, mirroring
 * {@code CouponAnvilPlaceholder} — tagged via PDC so it can be recognized
 * and discarded on close instead of leaking into the player's inventory.
 */
final class NicknameAnvilPlaceholder {

    private NicknameAnvilPlaceholder() {
    }

    static ItemStack create(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("여기에 한글 닉네임을 입력하세요", NamedTextColor.YELLOW));
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
        return new NamespacedKey(plugin, "nickname_anvil_placeholder");
    }
}
