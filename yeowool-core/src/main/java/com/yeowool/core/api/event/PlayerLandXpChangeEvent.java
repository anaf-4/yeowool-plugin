package com.yeowool.core.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired synchronously by {@link com.yeowool.core.api.service.LandStatService#addLandXp}
 * whenever a player's stored land XP changes. YeowoolCore only stores the raw
 * number (see {@link com.yeowool.core.api.service.LandStatService}'s class
 * javadoc); YeowoolLand listens for this to decide level-ups against its own
 * threshold table and calls {@code setLandLevel} back if crossed.
 *
 * <p>Must only be fired/handled on the main thread, same as any other Bukkit
 * event — callers of {@code addLandXp} are expected to run on the main
 * thread already since it mutates the same cached {@code PlayerData} that
 * join/quit/command handling touches.
 */
public final class PlayerLandXpChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final long delta;
    private final long newTotal;

    public PlayerLandXpChangeEvent(UUID uuid, long delta, long newTotal) {
        this.uuid = uuid;
        this.delta = delta;
        this.newTotal = newTotal;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getDelta() {
        return delta;
    }

    public long getNewTotal() {
        return newTotal;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
