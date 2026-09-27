package com.yeowool.life.treasure;

import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Map drops ride on {@link PlayerRepeatableActionEvent}, which mining,
 * fishing (both fishing paths) and hunting already fire once per completed
 * action. Digging is a sneak + right-click on any block; cancelled events
 * are still handled because digging never changes a block, so land
 * protection that cancels interactions doesn't apply.
 */
public final class TreasureListener implements Listener {

    private final TreasureService service;

    public TreasureListener(TreasureService service) {
        this.service = service;
    }

    @EventHandler
    public void onAction(PlayerRepeatableActionEvent event) {
        Player player = Bukkit.getPlayer(event.getUuid());
        if (player != null) {
            service.onAction(player, event.getActionType());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.getPlayer().isSneaking()) {
            return;
        }
        service.mapItem().read(event.getItem()).ifPresent(data -> {
            event.setCancelled(true);
            service.dig(event.getPlayer(), data);
        });
    }
}
