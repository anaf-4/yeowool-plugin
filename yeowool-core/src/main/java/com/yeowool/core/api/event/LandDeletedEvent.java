package com.yeowool.core.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired on the main thread by YeowoolLand whenever a land is disbanded, so
 * plugins that reference lands by id (e.g. YeowoolFederation) can clean up
 * without YeowoolLand having to know about their tables.
 */
public final class LandDeletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID landId;

    public LandDeletedEvent(UUID landId) {
        this.landId = landId;
    }

    public UUID getLandId() {
        return landId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
