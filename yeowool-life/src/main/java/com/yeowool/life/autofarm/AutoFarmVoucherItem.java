package com.yeowool.life.autofarm;

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
import java.util.Locale;

/**
 * PDC-tagged 자동줍기권/자동심기권 items — each item unit is worth a fixed
 * {@code charges} amount, shown in its lore as "N회" (or "무제한"). Items with
 * the same embedded amount stack normally (identical PDC ⇒ identical meta),
 * so a player can hold several at once. Right-click consumes exactly 1 item
 * from the stack; shift-right-click consumes the whole held stack at once
 * (see {@code AutoFarmVoucherListener}). Icon comes from the existing
 * {@code moafarm_items} ItemsAdder pack (see {@link AutoFarmType#iconId()}).
 */
public final class AutoFarmVoucherItem {

    private final NamespacedKey typeKey;
    private final NamespacedKey chargesKey;

    public AutoFarmVoucherItem(JavaPlugin plugin) {
        this.typeKey = new NamespacedKey(plugin, "autofarm_voucher_type");
        this.chargesKey = new NamespacedKey(plugin, "autofarm_voucher_charges");
    }

    public ItemStack create(AutoFarmType type, long charges) {
        ItemStack stack = resolveIcon(type);
        stack.setAmount(1);
        applyMeta(stack, type, charges);
        return stack;
    }

    public AutoFarmType typeOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return AutoFarmType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public long chargesOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Long charges = meta.getPersistentDataContainer().get(chargesKey, PersistentDataType.LONG);
        return charges == null ? 0 : charges;
    }

    private void applyMeta(ItemStack stack, AutoFarmType type, long charges) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(type.voucherName(), NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        String amountText = charges >= AutoFarmType.INFINITE ? "무제한" : String.format("%,d회", charges);
        meta.lore(List.of(
                Component.text("1개당 충전량: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(amountText, NamedTextColor.AQUA)),
                Component.text("우클릭: 1개 사용", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("쉬프트+우클릭: 보유한 만큼 한번에 사용", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(chargesKey, PersistentDataType.LONG, charges);
        stack.setItemMeta(meta);
    }

    private ItemStack resolveIcon(AutoFarmType type) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(type.iconId());
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.PAPER);
    }
}
