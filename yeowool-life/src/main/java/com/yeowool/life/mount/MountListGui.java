package com.yeowool.life.mount;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** {@code /탈것}: the viewer's unlocked mounts; clicking one opens MCPets' own menu to summon and ride. */
public final class MountListGui extends YeowoolGui {

    public static final int SIZE = 27;

    public MountListGui(List<MountDefinition> owned) {
        super(SIZE, Component.text("내 탈것", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < owned.size() && i < SIZE; i++) {
            setButton(i, GuiButton.of(icon(owned.get(i)), event -> {
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                player.performCommand("mcpets");
            }));
        }
    }

    private static ItemStack icon(MountDefinition mount) {
        ItemStack stack = new ItemStack(mount.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(mount.displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭: 펫 메뉴에서 소환·탑승", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (mount.customModelData() != 0) {
            meta.setCustomModelData(mount.customModelData());
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
