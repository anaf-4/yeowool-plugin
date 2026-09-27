package com.yeowool.life.treasure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Treasure map math kept free of Bukkit so it can be unit tested. */
public final class TreasureRules {

    private static final String[] COMPASS = {"북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽"};
    private static final int BAND = 200;

    private TreasureRules() {
    }

    /** 8-way compass for the vector (dx, dz) in Minecraft coordinates: +X is east, -Z is north. */
    public static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) {
            angle += 360;
        }
        return COMPASS[(int) Math.round(angle / 45.0) % 8];
    }

    /** Distance shown to the player: 50-block steps beyond 200 blocks, 10-block steps within, never 0. */
    public static long roughDistance(double distance) {
        long step = distance > 200 ? 50 : 10;
        return Math.max(step, Math.round(distance / step) * step);
    }

    /** Uniform angle and uniform radius in [minRadius, maxRadius] around the center; returns {x, z}. */
    public static int[] randomSpot(Random random, int centerX, int centerZ, int minRadius, int maxRadius) {
        int min = Math.max(0, minRadius);
        int max = Math.max(min, maxRadius);
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = min + random.nextDouble() * (max - min);
        return new int[]{
                centerX + (int) Math.round(Math.cos(angle) * radius),
                centerZ + (int) Math.round(Math.sin(angle) * radius)
        };
    }

    /** Weighted pick; missing, negative or all-zero weights fall back to COMMON. */
    public static TreasureTier pickTier(Random random, Map<TreasureTier, Integer> weights) {
        int total = 0;
        for (TreasureTier tier : TreasureTier.values()) {
            total += Math.max(0, weights.getOrDefault(tier, 0));
        }
        if (total <= 0) {
            return TreasureTier.COMMON;
        }
        int roll = random.nextInt(total);
        for (TreasureTier tier : TreasureTier.values()) {
            int weight = Math.max(0, weights.getOrDefault(tier, 0));
            if (roll < weight) {
                return tier;
            }
            roll -= weight;
        }
        return TreasureTier.COMMON;
    }

    /** The 200-block band containing {@code coord}, e.g. 1234 → "1200~1400" — the rough area written on the map. */
    public static String band(int coord) {
        int low = Math.floorDiv(coord, BAND) * BAND;
        return low + "~" + (low + BAND);
    }

    /** Up to {@code count} distinct elements of {@code pool} in random order. */
    public static <T> List<T> pickItems(List<T> pool, int count, Random random) {
        List<T> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, Math.max(0, Math.min(count, copy.size()))));
    }

    /** Uniform in [min, max]; max ≤ min returns max(0, min). */
    public static long randomMoney(Random random, long min, long max) {
        if (max <= min) {
            return Math.max(0, min);
        }
        return min + (long) Math.floor(random.nextDouble() * (max - min + 1));
    }
}
