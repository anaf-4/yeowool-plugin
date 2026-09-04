package com.yeowool.market.npcshop;

import com.yeowool.core.api.model.CurrencyType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /상점아이템설정 <상점ID> <구매가> <판매가> [온|캐시]} stamps these PDC tags
 * onto the admin's held item (plus a couple of throwaway lore lines so the
 * admin can see the price while placing it into {@code AdminShopEditorGui}).
 * Once that editor's page is saved, {@code AdminShopStore} reads the tags
 * back off the placed item to build a real {@link ShopItem} and regenerates
 * a clean icon via {@link com.yeowool.market.util.ItemResolver} for players
 * — so this stamped lore never actually reaches a customer-facing shop.
 */
public final class ShopPricedItem {

    private ShopPricedItem() {
    }

    private static NamespacedKey buyKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "shop_item_buy_price");
    }

    private static NamespacedKey sellKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "shop_item_sell_price");
    }

    private static NamespacedKey currencyKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "shop_item_currency");
    }

    private static NamespacedKey strictMatchKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "shop_item_strict_match");
    }

    public static void stamp(JavaPlugin plugin, ItemStack item, long buyPrice, long sellPrice, CurrencyType currency, boolean strictMatch) {
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(buyKey(plugin), PersistentDataType.LONG, buyPrice);
        pdc.set(sellKey(plugin), PersistentDataType.LONG, sellPrice);
        pdc.set(currencyKey(plugin), PersistentDataType.STRING, currency.name());
        pdc.set(strictMatchKey(plugin), PersistentDataType.BYTE, (byte) (strictMatch ? 1 : 0));

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            for (Component line : meta.lore()) {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                if (!plain.startsWith("구매가:") && !plain.startsWith("판매가:") && !plain.startsWith("엄격 매칭:")) {
                    lore.add(line);
                }
            }
        }
        lore.add(Component.text("구매가: " + (buyPrice > 0 ? String.format("%,d", buyPrice) + currency.displayName() : "판매 전용"), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("판매가: " + (sellPrice > 0 ? String.format("%,d", sellPrice) + currency.displayName() : "구매 전용"), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        if (strictMatch) {
            lore.add(Component.text("엄격 매칭: 이름/설명/인챈트/손상이 없어야 판매 가능", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public static long buyPrice(JavaPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? 0 : meta.getPersistentDataContainer().getOrDefault(buyKey(plugin), PersistentDataType.LONG, 0L);
    }

    public static long sellPrice(JavaPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? 0 : meta.getPersistentDataContainer().getOrDefault(sellKey(plugin), PersistentDataType.LONG, 0L);
    }

    public static CurrencyType currency(JavaPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String name = meta == null ? null : meta.getPersistentDataContainer().get(currencyKey(plugin), PersistentDataType.STRING);
        try {
            return name == null ? CurrencyType.ON : CurrencyType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CurrencyType.ON;
        }
    }

    public static boolean strictMatch(JavaPlugin plugin, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().getOrDefault(strictMatchKey(plugin), PersistentDataType.BYTE, (byte) 0) != 0;
    }
}
