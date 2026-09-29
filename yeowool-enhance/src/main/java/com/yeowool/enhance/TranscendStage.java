package com.yeowool.enhance;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * One transcendence step from config.yml: its display name/color, the stat multiplier used from then
 * on, what the attempt costs (stone item + 온) and succeeds with, and how much pricier normal enhancing
 * becomes at this stage.
 */
public record TranscendStage(int stage, String name, NamedTextColor color, double statMultiplier,
                             String stoneItemId, int stoneAmount, long currency, double successRate,
                             double enhanceCostMultiplier) {
}
