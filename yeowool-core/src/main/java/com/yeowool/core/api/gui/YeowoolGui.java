package com.yeowool.core.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Common base for every GUI menu across the Yeowool plugins, so shop
 * screens, land menus, and admin panels share one click-dispatch mechanism
 * instead of each plugin writing its own {@code InventoryClickEvent}
 * listener. {@link com.yeowool.core.gui.GuiListener} (registered once by
 * YeowoolCore) routes clicks to {@link #getButtons()} and cancels the event
 * by default so items can never be dragged out — except slots explicitly
 * marked {@link #setEditableSlot(int)}, for GUIs like YeowoolMarket's trade
 * screen that need the player to actually place items into the menu.
 */
public abstract class YeowoolGui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private final Set<Integer> editableSlots = new HashSet<>();

    protected YeowoolGui(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    protected void setButton(int slot, GuiButton button) {
        buttons.put(slot, button);
        inventory.setItem(slot, button.getItem());
    }

    protected void clearButton(int slot) {
        buttons.remove(slot);
        inventory.setItem(slot, null);
    }

    protected void setEditableSlot(int slot) {
        editableSlots.add(slot);
    }

    public boolean isEditableSlot(int slot) {
        return editableSlots.contains(slot);
    }

    public Map<Integer, GuiButton> getButtons() {
        return buttons;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Called by {@link com.yeowool.core.gui.GuiListener} when the player
     * closes this GUI. Override to persist state, refund held items, etc.
     */
    public void onClose(Player player) {
        // no-op by default
    }

    public void handleClick(InventoryClickEvent event) {
        GuiButton button = buttons.get(event.getRawSlot());
        if (button != null) {
            button.click(event);
        }
    }

    /**
     * Called one tick after a click into an {@link #isEditableSlot(int)}
     * finishes, once the item has actually moved. Override to read the new
     * contents and react (e.g. re-sync a trade session).
     */
    public void onEditableSlotChanged() {
        // no-op by default
    }
}
