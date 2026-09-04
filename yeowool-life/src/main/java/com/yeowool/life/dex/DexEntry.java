package com.yeowool.life.dex;

import org.bukkit.Material;

/**
 * One collectible entry in a non-fishing {@code /도감} category (광물/사냥/
 * 작물). {@code id} is deliberately the exact vanilla {@code Material} or
 * {@code EntityType} name (e.g. {@code DIAMOND_ORE}, {@code ZOMBIE}) rather
 * than an arbitrary custom key like {@code FishSpecies} uses — the
 * mining/farming/hunting listeners react to real game events and can just
 * use {@code event.getBlock().getType().name()} / {@code entity.getType().name()}
 * directly to build the statistic key, with no id-lookup table needed on
 * that side at all.
 */
public record DexEntry(String id, String display, Material icon) {
}
