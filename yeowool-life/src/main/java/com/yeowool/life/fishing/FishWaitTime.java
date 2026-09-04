package com.yeowool.life.fishing;

/** Custom min/max bite-wait time (in ticks) applied to every cast's {@code FishHook} instead of vanilla's own range. */
public record FishWaitTime(int minTicks, int maxTicks) {
}
