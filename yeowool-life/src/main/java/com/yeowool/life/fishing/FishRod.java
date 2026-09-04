package com.yeowool.life.fishing;

/**
 * A tiered custom fishing rod ({@code customItemId}, an ItemsAdder namespaced
 * id — real vanilla {@code FISHING_ROD} material underneath, just re-textured,
 * so casting still fires {@code PlayerFishEvent} normally). Holding one in the
 * main hand while fishing multiplies every rarity tier's weight except the
 * lowest (first-listed) one in {@code fishing.rarities}, shifting the roll
 * toward rarer catches — see {@link FishingListener#rollRarity}.
 */
public record FishRod(String id, String displayName, String customItemId, double rareWeightMultiplier) {
}
