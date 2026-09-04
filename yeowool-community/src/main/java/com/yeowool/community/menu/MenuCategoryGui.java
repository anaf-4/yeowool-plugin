package com.yeowool.community.menu;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

/**
 * One category screen off {@link MenuHubGui} — a plain steampunk-themed background (or one of the
 * named templates, when it happens to fit thematically) with its entries auto-centered in the row
 * right under the title, and a "뒤로가기" back button centered in the last row. Slot placement is
 * a starting draft — nudge {@code entries}' order/count or these two row offsets once it's visible
 * in-game, same as every other GUI in this project ended up needing a live tuning pass.
 */
public final class MenuCategoryGui extends YeowoolGui {

    public MenuCategoryGui(String backgroundImageId, int rows, int offsetPx, Component title, List<MenuEntry> entries, Consumer<Player> onBack) {
        super(rows * 9, MenuBackgroundImages.title(offsetPx, backgroundImageId, title));

        // rows==1 has nowhere else to put entries but the back button's row; rows>=2 always keeps
        // them off that row so a wide entry list can never silently overwrite the back button.
        int entryRow = rows <= 1 ? 0 : Math.min(1, rows - 2);
        int startCol = Math.max(0, (9 - entries.size()) / 2);
        for (int i = 0; i < entries.size() && i < 9; i++) {
            int slot = entryRow * 9 + startCol + i;
            MenuEntry entry = entries.get(i);
            setButton(slot, GuiButton.of(entryIcon(entry), event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    entry.action().accept(player);
                }
            }));
        }

        int backSlot = (rows - 1) * 9 + 4;
        setButton(backSlot, GuiButton.of(backIcon(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                onBack.accept(player);
            }
        }));
    }

    private ItemStack entryIcon(MenuEntry entry) {
        ItemStack stack = entry.iconId() == null ? null : MenuBackgroundImages.icon(entry.iconId());
        if (stack != null) {
            stack = stack.clone();
        } else if (entry.fallbackMaterial() != null) {
            stack = new ItemStack(entry.fallbackMaterial());
        } else {
            stack = MenuBackgroundImages.transparentIcon();
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(entry.label().decoration(TextDecoration.ITALIC, false));
        if (!entry.lore().isEmpty()) {
            meta.lore(entry.lore().stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack backIcon() {
        ItemStack stack = MenuBackgroundImages.transparentIcon();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("뒤로가기", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
