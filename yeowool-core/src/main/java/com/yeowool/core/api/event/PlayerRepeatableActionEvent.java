package com.yeowool.core.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired whenever a player completes an action that's meaningful to repeat
 * quickly and automate (fishing catch, ore break, ...) — a generic hook so
 * YeowoolAdmin's macro-timing heuristic can watch for suspiciously regular
 * intervals without YeowoolLife (or any other content plugin) needing a
 * compile dependency on YeowoolAdmin. {@code actionType} is a short,
 * plugin-defined tag (e.g. {@code "fishing"}, {@code "mining"}).
 */
public final class PlayerRepeatableActionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final String actionType;

    public PlayerRepeatableActionEvent(UUID uuid, String actionType) {
        this.uuid = uuid;
        this.actionType = actionType;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getActionType() {
        return actionType;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
