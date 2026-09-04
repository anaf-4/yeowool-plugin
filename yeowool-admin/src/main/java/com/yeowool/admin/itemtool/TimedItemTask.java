package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically scans every online non-OP player's inventory for timed
 * items: starts the countdown the first time one turns up on a non-OP
 * holder (per the requirement — OPs can carry a not-yet-started timed item
 * freely), and removes any that have expired. A repeating scan is simpler
 * and more robust than trying to catch every possible way an item can enter
 * an inventory (pickup, craft, hopper, trade, ...), and a whole-inventory
 * scan every few seconds is cheap even at 50 concurrent players.
 */
public final class TimedItemTask extends BukkitRunnable {

    private final MessageService messages;
    private final ItemFlags itemFlags;

    public TimedItemTask(MessageService messages, ItemFlags itemFlags) {
        this.messages = messages;
        this.itemFlags = itemFlags;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                continue;
            }
            scanInventory(player, player.getInventory());
        }
    }

    private void scanInventory(Player player, PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || !itemFlags.isTimed(item)) {
                continue;
            }

            if (!itemFlags.hasStartedCountdown(item)) {
                itemFlags.startCountdown(item);
                changed = true;
                continue;
            }

            if (itemFlags.isExpired(item)) {
                contents[slot] = null;
                changed = true;
                messages.send(player, "itemtool.timed-expired-notice");
            }
        }

        if (changed) {
            inventory.setContents(contents);
        }
    }
}
