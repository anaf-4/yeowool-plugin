package com.yeowool.life.fishing;

/**
 * The reel-in timing minigame shown via {@link org.bukkit.boss.BossBar} the
 * moment a fish bites: a bar fills from 0 to {@code totalDurationMs}, and
 * somewhere inside that window sits a "sweet spot"
 * ({@code sweetSpotMinWidthMs}–{@code sweetSpotMaxWidthMs} wide, placed
 * randomly per bite — see {@link FishingListener#onBite}) that turns the bar
 * green. Right-clicking to reel in while inside it is a "perfect" catch;
 * within {@code goodBufferMs} of either edge is "good"; further out risks
 * losing the fish entirely ({@code missFailChancePercent}).
 */
public record FishMinigameConfig(long totalDurationMs, long sweetSpotMinWidthMs, long sweetSpotMaxWidthMs, long goodBufferMs,
                                  double perfectRareBonus, double goodRareBonus,
                                  double perfectSizeBonusPercent, double goodSizeBonusPercent,
                                  double missFailChancePercent) {
}
