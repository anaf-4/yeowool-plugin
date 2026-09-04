package com.yeowool.teleport;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Cancels a pending teleport countdown if the player moves a full block or takes damage. */
public final class TeleportCancelListener implements Listener {

    private final TeleportService teleportService;
    private final MessageService messages;

    public TeleportCancelListener(TeleportService teleportService, MessageService messages) {
        this.teleportService = teleportService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!teleportService.cancelsOnMove() || !teleportService.hasPending(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        teleportService.cancelPending(event.getPlayer().getUniqueId());
        messages.send(event.getPlayer(), "teleport.cancelled-move");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !teleportService.hasPending(player.getUniqueId())) {
            return;
        }
        teleportService.cancelPending(player.getUniqueId());
        messages.send(player, "teleport.cancelled-damage");
    }
}
