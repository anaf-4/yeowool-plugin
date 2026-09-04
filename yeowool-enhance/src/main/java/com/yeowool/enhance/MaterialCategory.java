package com.yeowool.enhance;

import org.bukkit.Material;

/** Only weapons and armor carry a meaningful "성능"(stat) bonus — tools/blocks/etc. are never enhanceable. */
public enum MaterialCategory {
    WEAPON, ARMOR, NONE;

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
        return NONE;
    }
}
