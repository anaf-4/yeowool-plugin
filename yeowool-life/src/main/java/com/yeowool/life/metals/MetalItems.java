package com.yeowool.life.metals;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.metals.MetalConfig.Recipe;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pack items ({@code bundle_metals:<id><suffix>}) and the two 강화 보조 재료 — vanilla items tagged in PDC, read by
 * YeowoolEnhance's {@code EnhanceAid} (the key strings must stay identical on both sides).
 */
final class MetalItems {

    static final String NAMESPACE = "bundle_metals";
    static final NamespacedKey BOOSTER_KEY = new NamespacedKey("yeowool", "enhance_booster");
    static final NamespacedKey CHARM_KEY = new NamespacedKey("yeowool", "enhance_charm");

    private MetalItems() {
    }

    /** A fresh pack item, or null when ItemsAdder doesn't know it (pack missing / not loaded yet). */
    static ItemStack create(String metalId, int suffix, int amount) {
        CustomStack custom = CustomStack.getInstance(NAMESPACE + ":" + metalId + suffix);
        return custom == null ? null : custom.getItemStack().asQuantity(amount);
    }

    /** "blue6" for a {@code bundle_metals:blue6} stack, else null. */
    static String packId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null && NAMESPACE.equals(custom.getNamespace()) ? custom.getId() : null;
    }

    /** Main-inventory (not armor/offhand) count of pack items whose id matches. */
    static int count(Player player, Predicate<String> packId) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            String id = packId(stack);
            if (id != null && packId.test(id)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes {@code amount} matching items — callers check {@link #count} first, on the same tick. */
    static void take(Player player, Predicate<String> packId, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            String id = packId(contents[i]);
            if (id == null || !packId.test(id)) {
                continue;
            }
            int taken = Math.min(amount, contents[i].getAmount());
            contents[i].setAmount(contents[i].getAmount() - taken);
            amount -= taken;
        }
        inventory.setStorageContents(contents);
    }

    /** Gives, dropping whatever doesn't fit at the player's feet. */
    static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** 강화 촉진제 (amethyst shard) or 파괴 방지 부적 (paper drawn as a totem — never a working totem). */
    static ItemStack aid(Recipe recipe, MessageService messages, int amount) {
        ItemStack stack = new ItemStack(recipe.charm() ? Material.PAPER : Material.AMETHYST_SHARD, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(messages.resolveRaw(recipe.charm() ? "metals.item.charm-name" : "metals.item.booster-name",
                Placeholder.unparsed("name", recipe.name()), Placeholder.unparsed("bonus", String.valueOf(recipe.boosterPercent())))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        List<String> keys = recipe.charm() ? List.of("metals.item.charm-lore-1", "metals.item.charm-lore-2")
                : List.of("metals.item.booster-lore-1", "metals.item.booster-lore-2");
        meta.lore(keys.stream().map(key -> messages.resolveRaw(key, Placeholder.unparsed("bonus", String.valueOf(recipe.boosterPercent())))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
        meta.setEnchantmentGlintOverride(true);
        if (recipe.charm()) {
            meta.setItemModel(NamespacedKey.minecraft("totem_of_undying"));
            meta.getPersistentDataContainer().set(CHARM_KEY, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().set(BOOSTER_KEY, PersistentDataType.INTEGER, recipe.boosterPercent());
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
