package com.yeowool.enchant;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real {@link Material#ENCHANTED_BOOK} items skinned with the AE pack's
 * simple/unique/elite/ultimate/legend/fabled_book textures (still a genuine
 * enchanted book underneath — a normal anvil combines them onto gear
 * directly, no custom "apply" screen needed) with an {@link EnchantTier}
 * PDC tag on top (for Tinkerer/Alchemist tier lookups) and the plain
 * "마법 가루" (Magic Dust, vanilla {@link Material#GLOWSTONE_DUST}) currency
 * Tinkerer trades books for.
 */
public final class EnchantItems {

    private final NamespacedKey tierKey;
    private final EnchantConfig config;

    public EnchantItems(JavaPlugin plugin, EnchantConfig config) {
        this.tierKey = new NamespacedKey(plugin, "inchant_tier");
        this.config = config;
    }

    /** Rolls a random enchantment + level (from {@link EnchantConfig#enchantPool()}/the tier's level range) and bakes it into a real enchanted book. */
    public ItemStack createBook(EnchantTier tier) {
        List<Enchantment> pool = config.enchantPool();
        var setting = config.settingFor(tier);
        Enchantment enchantment = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        int level = ThreadLocalRandom.current().nextInt(setting.levelMin(), setting.levelMax() + 1);

        ItemStack stack = resolveBookSkin(tier);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(tier.displayName() + " 인챈트북", tier.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(enchantDisplayName(enchantment) + " " + level, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("모루에서 무기/방어구와 조합해 사용하세요.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(enchantment, level, true);
        }
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Plain tier icon with no enchant roll — for previews/buttons that shouldn't imply "this exact item" (the roll only happens when a book is actually {@link #createBook created}). */
    public ItemStack previewIcon(EnchantTier tier) {
        ItemStack stack = resolveBookSkin(tier);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(tier.displayName() + " 인챈트북", tier.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    /** The AE-pack-skinned {@code ENCHANTED_BOOK} for {@code tier}, falling back to a plain vanilla one if ItemsAdder/the pack isn't available. */
    private ItemStack resolveBookSkin(EnchantTier tier) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(tier.bookIconId());
            if (custom != null) {
                ItemStack stack = custom.getItemStack();
                if (stack.getType() == Material.ENCHANTED_BOOK) {
                    return stack;
                }
            }
        }
        return new ItemStack(Material.ENCHANTED_BOOK);
    }

    private String enchantDisplayName(Enchantment enchantment) {
        String[] parts = enchantment.getKey().getKey().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public boolean isBook(ItemStack stack) {
        return tierOf(stack) != null;
    }

    public EnchantTier tierOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return EnchantTier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public ItemStack createDust(int amount) {
        ItemStack stack = new ItemStack(Material.GLOWSTONE_DUST, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("마법 가루", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("틴커러에서 인챈트북을 분해하면 얻습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    public void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
