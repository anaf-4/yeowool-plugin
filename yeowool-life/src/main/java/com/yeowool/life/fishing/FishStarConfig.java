package com.yeowool.life.fishing;

/**
 * "별" (star) catch — mirrors CustomFishing's own silver/golden-star
 * mechanic: not a separate species roster, just an independent chance
 * ({@code chancePercent}) rolled on top of whatever species/rarity/size
 * already came out of the normal roll, boosting that catch's size by
 * {@code sizeBonusPercent} and swapping its displayed grade to "별" — see
 * {@link FishingListener#onCaught}.
 */
public record FishStarConfig(double chancePercent, double sizeBonusPercent) {
}
