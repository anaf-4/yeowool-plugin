package com.yeowool.enhance;

import org.bukkit.Material;

/** Weapons, armor and tools carry a meaningful "성능"(stat) bonus — everything else is never enhanceable. */
public enum MaterialCategory {
    WEAPON, ARMOR, TOOL, NONE;

    public static MaterialCategory of(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("TRIDENT")
                || name.equals("BOW") || name.equals("CROSSBOW")) {
            return WEAPON;
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET") || name.equals("ELYTRA")) {
            return ARMOR;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return TOOL;
        }
        return NONE;
    }
}
