package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** {@code my_warps} background — the viewer's own warps; click opens {@link PlayerWarpEditGui} for that one. */
public final class PlayerWarpMyGui extends YeowoolGui {

    private static final int[] GRID_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};

    public PlayerWarpMyGui(PlayerWarpContext ctx, PlayerWarpTextInput textInput, Player viewer) {
        super(54, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "my_warps",
                Component.text("내 워프", NamedTextColor.GOLD)));

        List<PlayerWarp> mine = ctx.warps().ownedBy(viewer.getUniqueId()).values().stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
        for (int i = 0; i < mine.size() && i < GRID_SLOTS.length; i++) {
            PlayerWarp warp = mine.get(i);
            setButton(GRID_SLOTS[i], GuiButton.of(buildIcon(warp), event ->
                    new PlayerWarpEditGui(ctx, warp, textInput).open((Player) event.getWhoClicked())));
        }
        if (mine.isEmpty()) {
            setButton(22, GuiButton.display(PlayerWarpIcons.icon("playerwarps_gui:no_warpsicon", Material.BARRIER, "등록된 워프가 없습니다", NamedTextColor.GRAY)));
        }

        setButton(49, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_home", Material.BARRIER, "닫기", NamedTextColor.GRAY), event ->
                event.getWhoClicked().closeInventory()));
    }

    private ItemStack buildIcon(PlayerWarp warp) {
        ItemStack stack = PlayerWarpIcons.resolveCustom("playerwarps_gui:default_warpitem");
        if (stack == null) {
            stack = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(warp.effectiveDisplayName(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = List.of(
                Component.text("상태: " + (warp.status() == PlayerWarp.Status.OPENED ? "공개" : "비공개"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("클릭하여 설정 편집", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
