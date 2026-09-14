package com.yeowool.admin.coupon;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Whatever's in your main inventory" as a coupon reward payload — shared by
 * {@link CouponCreateCommand} and {@link CouponManageCommand}'s item-edit
 * subcommand so both take/return stacks the same way.
 */
final class CouponInventoryPayload {

    private CouponInventoryPayload() {
    }

    /** Snapshots every non-empty stack in {@code player}'s hotbar+main inventory (not armor/off-hand) and clears those slots. */
    static List<ItemStack> take(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<ItemStack> taken = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) {
                taken.add(item.clone());
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        return taken;
    }

    /** Undoes {@link #take} when the coupon couldn't actually be created (e.g. duplicate code). */
    static void giveBack(Player player, List<ItemStack> items) {
        var leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
}
