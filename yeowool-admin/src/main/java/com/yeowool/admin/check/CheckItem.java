package com.yeowool.admin.check;

import com.yeowool.core.api.model.CurrencyType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * A physical "수표" (check) — {@code /수표 <금액>} / {@code /유료수표 <금액>}
 * create one of these for whichever {@link CurrencyType} was asked for, and
 * {@link CheckRedeemListener} credits the holder's matching balance and
 * consumes one on right-click. Unlike the coupon/nickname vouchers, there's
 * no code to type: the amount and currency are baked into the item itself
 * via PDC at creation time, so redemption is a single click.
 */
public final class CheckItem {

    private CheckItem() {
    }

    private static NamespacedKey markerKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "check_marker");
    }

    private static NamespacedKey amountKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "check_amount");
    }

    private static NamespacedKey currencyKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "check_currency");
    }

    public static ItemStack create(JavaPlugin plugin, long amount, CurrencyType currency) {
        ItemStack item = new ItemStack(currency == CurrencyType.CASH ? Material.SUNFLOWER : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(String.format("%,d", amount) + currency.displayName() + " 수표", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하여 사용", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
        pdc.set(amountKey(plugin), PersistentDataType.LONG, amount);
        pdc.set(currencyKey(plugin), PersistentDataType.STRING, currency.name());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCheck(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(markerKey(plugin), PersistentDataType.BYTE);
    }

    public static long amount(JavaPlugin plugin, ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(amountKey(plugin), PersistentDataType.LONG, 0L);
    }

    public static CurrencyType currency(JavaPlugin plugin, ItemStack item) {
        String name = item.getItemMeta().getPersistentDataContainer().get(currencyKey(plugin), PersistentDataType.STRING);
        try {
            return name == null ? CurrencyType.ON : CurrencyType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CurrencyType.ON;
        }
    }
}
