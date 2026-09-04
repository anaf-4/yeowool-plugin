package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Prevents a non-OP player from dropping a bound item. An OP is always
 * allowed to drop it — binding protects against players losing/trading it,
 * not against staff managing it.
 */
public final class BoundItemProtectionListener implements Listener {

    private final MessageService messages;
    private final ItemFlags itemFlags;

    public BoundItemProtectionListener(MessageService messages, ItemFlags itemFlags) {
        this.messages = messages;
        this.itemFlags = itemFlags;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().isOp()) {
            return;
        }
        if (itemFlags.isBound(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "itemtool.bound-cannot-drop");
        }
    }
}
