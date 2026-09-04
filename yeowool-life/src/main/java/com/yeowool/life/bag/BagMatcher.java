package com.yeowool.life.bag;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Decides which {@link BagType} (if any) a dropped/picked-up item belongs
 * to. ItemsAdder namespace is checked first (CustomCrops crops, this
 * server's {@code fishing_expansion} fish pack), then a plain vanilla
 * material fallback for items neither pack covers.
 */
final class BagMatcher {

    private static final Set<Material> CROP_MATERIALS = Set.of(
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
            Material.NETHER_WART, Material.COCOA_BEANS, Material.PUMPKIN,
            Material.MELON_SLICE, Material.SWEET_BERRIES);

    private static final Set<Material> ORE_MATERIALS = Set.of(
            Material.COAL, Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,
            Material.DIAMOND, Material.EMERALD, Material.LAPIS_LAZULI, Material.REDSTONE,
            Material.QUARTZ, Material.NETHERITE_SCRAP,
            // silk-touched ore blocks
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS);

    private static final Set<Material> FISH_MATERIALS = Set.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private static final Set<Material> LIVESTOCK_MATERIALS = Set.of(
            Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.MUTTON, Material.COOKED_MUTTON, Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.RABBIT, Material.COOKED_RABBIT, Material.RABBIT_HIDE, Material.RABBIT_FOOT,
            Material.LEATHER, Material.FEATHER, Material.EGG, Material.MILK_BUCKET);

    private BagMatcher() {
    }

    static BagType classify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.byItemStack(stack);
            if (custom != null) {
                String namespace = custom.getNamespacedID().split(":", 2)[0];
                if (namespace.equals("customcrops")) {
                    return BagType.CROP;
                }
                if (namespace.equals("fishing_expansion")) {
                    return BagType.FISH;
                }
            }
        }
        Material material = stack.getType();
        if (CROP_MATERIALS.contains(material)) {
            return BagType.CROP;
        }
        if (ORE_MATERIALS.contains(material)) {
            return BagType.ORE;
        }
        if (FISH_MATERIALS.contains(material)) {
            return BagType.FISH;
        }
        if (LIVESTOCK_MATERIALS.contains(material)) {
            return BagType.LIVESTOCK;
        }
        return null;
    }
}
