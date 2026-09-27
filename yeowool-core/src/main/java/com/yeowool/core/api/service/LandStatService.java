package com.yeowool.core.api.service;

import java.util.Set;
import java.util.UUID;

/**
 * Raw storage for the land-related fields on the core player row (land
 * level, land XP, owned land ids). YeowoolLand / YeowoolLandLevel own the
 * actual level-up thresholds, chunk grants, and land records; this service is
 * just where those plugins persist the denormalized summary that other
 * plugins (profile, scoreboard, achievements) read cheaply without depending
 * on YeowoolLand directly.
 */
public interface LandStatService {

    int getLandLevel(UUID uuid);

    void setLandLevel(UUID uuid, int level);

    long getLandXp(UUID uuid);

    long addLandXp(UUID uuid, long amount);

    /**
     * Server-wide multiplier applied to every {@link #addLandXp} call (1.0 =
     * normal). Meant for YeowoolCommunity's event system ("이벤트 - XP 2배"
     * type effects) without every XP source needing to know about events.
     */
    void setXpMultiplier(double multiplier);

    /** Effective land XP multiplier: the larger of the manual one above and {@link #setAutoXpBoost}. */
    double getXpMultiplier();

    /**
     * Server-wide multiplier applied to crop harvest drop amounts (1.0 =
     * normal). Same "이벤트 - 작물 드랍 2배" purpose as {@link #setXpMultiplier}.
     */
    void setCropDropMultiplier(double multiplier);

    /** Effective crop drop multiplier: the larger of the manual one above and {@link #setAutoCropDropBoost}. */
    double getCropDropMultiplier();

    /**
     * Automatic (surprise event) boosts, kept apart from the manual event multipliers above so the
     * two never reset each other; the larger of manual and automatic applies (1.0 = none).
     */
    void setAutoXpBoost(double multiplier);

    void setAutoCropDropBoost(double multiplier);

    Set<UUID> getLandIds(UUID uuid);

    void addLandId(UUID uuid, UUID landId);

    void removeLandId(UUID uuid, UUID landId);
}
