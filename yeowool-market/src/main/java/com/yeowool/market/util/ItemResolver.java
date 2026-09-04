package com.yeowool.market.util;

import com.yeowool.market.npcshop.ShopItem;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.logging.Logger;

/**
 * Turns a {@link ShopItem} into an actual {@link ItemStack}, and checks
 * whether a stack in a player's inventory matches one — covering both plain
 * vanilla materials and ItemsAdder custom items. Every ItemsAdder API call
 * is only reached when {@link ShopItem#isCustomItem()} is true, so this
 * class loads fine even on a server without ItemsAdder installed as long as
 * no shop actually configures a custom item id.
 */
public final class ItemResolver {

    private ItemResolver() {
    }

    public static ItemStack build(ShopItem item, int amount, Logger logger) {
        ItemStack stack;
        if (item.isCustomItem()) {
            CustomStack custom = CustomStack.getInstance(item.itemId());
            if (custom == null) {
                logger.warning("ItemsAdder 커스텀 아이템을 찾을 수 없습니다: " + item.itemId());
                return new ItemStack(org.bukkit.Material.BARRIER, amount);
            }
            stack = custom.getItemStack();
            stack.setAmount(amount);
        } else {
            stack = new ItemStack(item.material(), amount);
            if (item.hasCustomModelData()) {
                ItemMeta meta = stack.getItemMeta();
                meta.setCustomModelData(item.customModelData());
                stack.setItemMeta(meta);
            }
        }
        if (item.customDisplayName() != null) {
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(GsonComponentSerializer.gson().deserialize(item.customDisplayName()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * True if {@code stack} is the same item {@code item} refers to,
     * regardless of stack size — used to count/remove matching items when
     * a player sells to an NPC shop.
     */
    public static boolean matches(ItemStack stack, ShopItem item) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        boolean sameKind;
        if (item.isCustomItem()) {
            CustomStack custom = CustomStack.byItemStack(stack);
            sameKind = custom != null && custom.getNamespacedID().equals(item.itemId());
        } else {
            sameKind = stack.getType() == item.material();
        }
        return sameKind && (!item.strictMatch() || isPlain(stack));
    }

    /**
     * No custom name, lore, enchantments, or damage — just the item as it
     * comes out of the shop. Deliberately doesn't check custom model data,
     * since a vanilla item's shop icon may legitimately carry one as its
     * intended "도트" texture (see {@link ShopItem#hasCustomModelData()}).
     */
    private static boolean isPlain(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) {
            return false;
        }
        return !(meta instanceof org.bukkit.inventory.meta.Damageable damageable && damageable.hasDamage());
    }

    public static boolean isItemsAdderAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }
}
