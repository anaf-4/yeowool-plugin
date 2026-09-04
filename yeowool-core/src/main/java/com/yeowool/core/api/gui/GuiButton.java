package com.yeowool.core.api.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * One clickable slot inside a {@link YeowoolGui}: the item shown, and what
 * happens when a player clicks it.
 */
public final class GuiButton {

    private final ItemStack item;
    private final Consumer<InventoryClickEvent> onClick;

    public GuiButton(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        this.item = item;
        this.onClick = onClick;
    }

    public static GuiButton of(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        return new GuiButton(item, onClick);
    }

    public static GuiButton display(ItemStack item) {
        return new GuiButton(item, event -> {});
    }

    public ItemStack getItem() {
        return item;
    }

    public void click(InventoryClickEvent event) {
        onClick.accept(event);
    }
}
