package com.yeowool.life.bag;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** {@code /가방} — pick one of the 4 bags to open its {@link BagGui}. */
public final class BagHubGui extends YeowoolGui {

    private static final int[] SLOTS = {10, 12, 14, 16};

    public BagHubGui(BagManager manager, Player viewer) {
        super(27, Component.text("가방", NamedTextColor.DARK_GREEN));

        BagType[] types = BagType.values();
        for (int i = 0; i < types.length; i++) {
            BagType type = types[i];
            int used = manager.usedSlots(viewer.getUniqueId(), type);
            int capacity = manager.capacity(viewer.getUniqueId(), type);

            ItemStack icon = new ItemStack(type.menuIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(type.label(), NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("사용 중: " + used + "/" + capacity, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            icon.setItemMeta(meta);

            setButton(SLOTS[i], GuiButton.of(icon, event -> new BagGui(manager, type, viewer).open(viewer)));
        }
    }
}
