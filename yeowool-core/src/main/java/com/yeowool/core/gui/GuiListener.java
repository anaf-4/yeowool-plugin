package com.yeowool.core.gui;

import com.yeowool.core.api.gui.YeowoolGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Single global listener backing every {@link YeowoolGui} across all
 * Yeowool plugins. Registered once by YeowoolCore so no other plugin needs
 * its own inventory-click listener for menus.
 */
public final class GuiListener implements Listener {

    private final JavaPlugin plugin;

    public GuiListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof YeowoolGui gui)) {
            return;
        }

        // A click into the player's OWN inventory (picking an item up onto the
        // cursor to then place into the GUI) must never be cancelled just
        // because a YeowoolGui happens to be open — only a click into the top
        // (GUI) inventory itself can land on a protected, non-editable slot.
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!clickedTop) {
            // Vanilla shift-click auto-placement scans the WHOLE top inventory for a free/
            // stackable slot, ignoring which ones a YeowoolGui actually marked editable - left
            // unhandled, it silently drops the item into a protected background slot that no
            // GUI ever reads back, destroying it the moment the menu closes. Route it ourselves
            // so a shift-click can only ever land in an editable slot (or not move at all).
            if (event.isShiftClick()) {
                handleShiftClickIntoGui(event, gui);
            }
            return;
        }

        boolean editableTopClick = gui.isEditableSlot(event.getSlot());
        if (!editableTopClick) {
            event.setCancelled(true);
        }
        gui.handleClick(event);
        if (editableTopClick) {
            plugin.getServer().getScheduler().runTask(plugin, gui::onEditableSlotChanged);
        }
    }

    private void handleShiftClickIntoGui(InventoryClickEvent event, YeowoolGui gui) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        event.setCancelled(true);

        Inventory top = event.getView().getTopInventory();
        ItemStack remaining = moving.clone();
        for (int slot = 0; slot < top.getSize() && remaining.getAmount() > 0; slot++) {
            if (!gui.isEditableSlot(slot)) {
                continue;
            }
            ItemStack existing = top.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                ItemStack placed = remaining.clone();
                top.setItem(slot, placed);
                remaining.setAmount(0);
            } else if (existing.isSimilar(remaining)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space <= 0) {
                    continue;
                }
                int moveAmount = Math.min(space, remaining.getAmount());
                existing.setAmount(existing.getAmount() + moveAmount);
                top.setItem(slot, existing);
                remaining.setAmount(remaining.getAmount() - moveAmount);
            }
        }

        if (remaining.getAmount() == moving.getAmount()) {
            return; // no editable slot could take it - leave the player's item untouched
        }
        event.setCurrentItem(remaining.getAmount() > 0 ? remaining : null);
        plugin.getServer().getScheduler().runTask(plugin, gui::onEditableSlotChanged);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof YeowoolGui gui)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean allEditable = event.getRawSlots().stream()
                .allMatch(rawSlot -> rawSlot >= topSize || gui.isEditableSlot(rawSlot));
        if (!allEditable) {
            event.setCancelled(true);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, gui::onEditableSlotChanged);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof YeowoolGui gui
                && event.getPlayer() instanceof Player player) {
            gui.onClose(player);
        }
    }
}
