package com.yeowool.admin.gui;

import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A plain 54-slot editable inventory (every slot free-edit, see
 * {@link YeowoolGui#setEditableSlot(int)}) for "admin drops items in, closes
 * the window, that becomes the saved list" flows — coupon rewards, cash
 * package contents, and any future one like them. Whatever's left when the
 * admin closes it is passed to {@code onSave} in slot order.
 */
public final class ItemGridEditorGui extends YeowoolGui {

    private final Consumer<List<ItemStack>> onSave;

    public ItemGridEditorGui(String title, List<ItemStack> existingItems, Consumer<List<ItemStack>> onSave) {
        super(54, Component.text(title, NamedTextColor.GOLD));
        this.onSave = onSave;

        for (int slot = 0; slot < 54; slot++) {
            setEditableSlot(slot);
        }
        for (int i = 0; i < existingItems.size() && i < 54; i++) {
            getInventory().setItem(i, existingItems.get(i));
        }
    }

    @Override
    public void onClose(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        onSave.accept(items);
    }
}
