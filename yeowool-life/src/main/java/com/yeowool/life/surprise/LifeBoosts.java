package com.yeowool.life.surprise;

import com.yeowool.core.api.service.LandStatService;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Automatic boost multipliers (1.0 = normal), layered by source (surprise event, donation project) so
 * one source ending never resets another's; the largest of a type applies. Land XP / crop drop go to
 * core's automatic slot (core again takes the larger of that and a manual {@code /이벤트}); treasure
 * drop and job XP live here and are read by the treasure-map and job XP code.
 */
public final class LifeBoosts {

    private static final Map<SurpriseEventType, Map<String, Double>> LAYERS = new EnumMap<>(SurpriseEventType.class);
    private static volatile double treasureDrop = 1.0;
    private static volatile double jobXp = 1.0;

    private LifeBoosts() {
    }

    public static double treasureDropMultiplier() {
        return treasureDrop;
    }

    public static double jobXpMultiplier() {
        return jobXp;
    }

    /** Main thread: sets {@code source}'s boost of {@code type} (1.0 clears it) and applies the largest layer. */
    public static void set(LandStatService land, String source, SurpriseEventType type, double multiplier) {
        Map<String, Double> layers = LAYERS.computeIfAbsent(type, t -> new HashMap<>());
        if (multiplier == 1.0) {
            layers.remove(source);
        } else {
            layers.put(source, multiplier);
        }
        double effective = layers.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        switch (type) {
            case LAND_XP -> land.setAutoXpBoost(effective);
            case CROP_DROP -> land.setAutoCropDropBoost(effective);
            case TREASURE_DROP -> treasureDrop = effective;
            case JOB_XP -> jobXp = effective;
        }
    }
}
