package com.yeowool.life.farming;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once for every fully grown crop a player harvests — vanilla crops,
 * ItemsAdder custom crops and CustomCrops alike — so things like the life
 * competition can count harvests without each farming listener knowing them.
 */
public final class LifeHarvestEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public LifeHarvestEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
