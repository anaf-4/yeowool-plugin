package com.yeowool.life.fishing;

/**
 * A consumable bait ({@code customItemId}, an ItemsAdder namespaced id, real
 * vanilla {@code PAPER} material underneath). Held in the off-hand while
 * fishing, one is consumed per successful catch and its
 * {@code rareWeightMultiplier} stacks multiplicatively with any equipped
 * {@link FishRod}'s — see {@link FishingListener#rollRarity}.
 */
public record FishBait(String id, String displayName, String customItemId, double rareWeightMultiplier) {
}
