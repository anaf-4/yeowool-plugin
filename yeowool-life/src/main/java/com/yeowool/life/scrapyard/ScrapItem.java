package com.yeowool.life.scrapyard;

/**
 * One entry of {@code scrapyard.scrap-items} in config.yml — {@code weight}
 * is what counts toward the carry-weight slowness tiers, {@code lootWeight}
 * is this item's relative share of the loot-chest random roll (not a
 * percentage — just compared against the sum of every item's lootWeight).
 */
public record ScrapItem(String id, String materialOrItemId, String displayName, int weight, int lootWeight) {
}
