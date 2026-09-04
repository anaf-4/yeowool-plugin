package com.yeowool.life.bag;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * When a player's inventory has no room left for a picked-up item at all,
 * and that item matches one of the 4 {@link BagType}s, redirects it into
 * the matching bag instead of leaving it on the ground. Inventory with any
 * room (an empty slot, or a compatible non-full stack) is left to vanilla
 * pickup as normal — only the "completely full" case is intercepted.
 */
public final class BagAutoCollectListener implements Listener {

    private final BagManager manager;

    public BagAutoCollectListener(BagManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item groundItem = event.getItem();
        ItemStack stack = groundItem.getItemStack();
        if (hasRoom(player.getInventory(), stack)) {
            return;
        }

        BagManager.BagOfferResult result = manager.offer(player, stack);
        if (!result.handled()) {
            return;
        }

        event.setCancelled(true);
        ItemStack leftover = result.leftover();
        if (leftover == null) {
            groundItem.remove();
        } else {
            groundItem.setItemStack(leftover);
        }
    }

    private boolean hasRoom(PlayerInventory inventory, ItemStack stack) {
        int maxStack = stack.getMaxStackSize();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                return true;
            }
            if (slot.getAmount() < maxStack && slot.isSimilar(stack)) {
                return true;
            }
        }
        return false;
    }
}
