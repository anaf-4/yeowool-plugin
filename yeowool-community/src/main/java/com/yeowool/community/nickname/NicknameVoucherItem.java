package com.yeowool.community.nickname;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * The physical "한글 닉네임 설정권" item {@code /한글닉네임설정권} grants.
 * Base appearance comes from an ItemsAdder custom item id (configured as
 * {@code korean-nickname.voucher-item-id}, e.g. a golden-scroll texture)
 * when ItemsAdder is installed and the id resolves; otherwise falls back to
 * plain paper so the plugin still works without that addon. PDC-tagged the
 * same way {@code CouponAnvilPlaceholder} is, so {@link NicknameVoucherListener}
 * can recognize a right-clicked stack as this voucher.
 */
final class NicknameVoucherItem {

    private NicknameVoucherItem() {
    }

    static ItemStack create(JavaPlugin plugin, String customItemId) {
        ItemStack item = baseItem(customItemId);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("한글 닉네임 설정권", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하여 한글 닉네임을 설정합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("(사용 시 소모됩니다)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    static boolean isVoucher(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    private static ItemStack baseItem(String customItemId) {
        if (customItemId != null && !customItemId.isBlank() && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(customItemId);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.PAPER);
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "korean_nickname_voucher");
    }
}
