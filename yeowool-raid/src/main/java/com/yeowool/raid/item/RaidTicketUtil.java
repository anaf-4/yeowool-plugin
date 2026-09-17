package com.yeowool.raid.item;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class RaidTicketUtil {

    private RaidTicketUtil() {
    }

    public static boolean hasEnough(Player player, String itemsAdderId, int amount) {
        return countHeld(player, itemsAdderId) >= amount;
    }

    /** Call only after hasEnough(player, itemsAdderId, amount) returned true. */
    public static void remove(Player player, String itemsAdderId, int amount) {
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !matches(stack, itemsAdderId)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            if (take == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            remaining -= take;
        }
        if (remaining > 0) {
            throw new IllegalStateException("Tried to remove " + amount + "x " + itemsAdderId
                    + " from " + player.getName() + " but only found " + (amount - remaining));
        }
    }

    private static int countHeld(Player player, String itemsAdderId) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && matches(stack, itemsAdderId)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static boolean matches(ItemStack stack, String itemsAdderId) {
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null && itemsAdderId.equals(custom.getNamespacedID());
    }
}
