package com.yeowool.life.bag;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Plain chest-style storage GUI for one {@link BagType} — no custom
 * background pack was supplied for the bag system, so this is a functional
 * MiniMessage-titled screen. Slots {@code [0, capacity)} are real, editable
 * storage; any remaining slots up to the next multiple of 9 are shown as a
 * locked filler (unlockable via the {@code moafarm_items:ticket_chest}
 * expansion ticket) rather than rendered at all.
 */
public final class BagGui extends YeowoolGui {

    private final BagManager manager;
    private final BagType type;
    private final int capacity;

    public BagGui(BagManager manager, BagType type, Player viewer) {
        super(rows(manager.capacity(viewer.getUniqueId(), type)), title(type, manager, viewer));
        this.manager = manager;
        this.type = type;
        this.capacity = manager.capacity(viewer.getUniqueId(), type);
        manager.markGuiOpen(viewer.getUniqueId(), type);

        ItemStack[] contents = manager.contents(viewer.getUniqueId(), type);
        for (int slot = 0; slot < capacity; slot++) {
            setEditableSlot(slot);
            if (contents != null && slot < contents.length && contents[slot] != null) {
                getInventory().setItem(slot, contents[slot]);
            }
        }
        for (int slot = capacity; slot < getInventory().getSize(); slot++) {
            setButton(slot, lockedFiller());
        }
    }

    private static int rows(int capacity) {
        int size = ((capacity + 8) / 9) * 9;
        return Math.min(Math.max(size, 18), 54);
    }

    private static Component title(BagType type, BagManager manager, Player viewer) {
        int used = manager.usedSlots(viewer.getUniqueId(), type);
        int capacity = manager.capacity(viewer.getUniqueId(), type);
        return Component.text(type.label() + " (" + used + "/" + capacity + ")", NamedTextColor.DARK_GREEN);
    }

    private static GuiButton lockedFiller() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("잠김", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("가방 확장권으로 잠금 해제할 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return GuiButton.display(stack);
    }

    @Override
    public void onClose(Player player) {
        manager.markGuiClosed(player.getUniqueId(), type);
        ItemStack[] snapshot = new ItemStack[capacity];
        for (int slot = 0; slot < capacity; slot++) {
            snapshot[slot] = getInventory().getItem(slot);
        }
        manager.applyGuiContents(player.getUniqueId(), type, snapshot);
    }
}
