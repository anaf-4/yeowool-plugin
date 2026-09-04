package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code confirm_warp} background — "테레포트가 유료임을 알려주는" screen the
 * real plugin shows before a paid visit ({@code allow-teleport-accept-menu}).
 * Slot positions (9-12 accept, 14-17 deny) come straight from the real
 * plugin's own {@code config.yml} (confirm/deny-item-positions), which is
 * exactly how {@code confirm_warp.png} is laid out.
 */
public final class PlayerWarpConfirmGui extends YeowoolGui {

    private static final int[] ACCEPT_SLOTS = {9, 10, 11, 12};
    private static final int[] DENY_SLOTS = {14, 15, 16, 17};

    public PlayerWarpConfirmGui(PlayerWarpContext ctx, PlayerWarp warp) {
        super(27, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "confirm_warp",
                Component.text("이동 확인", NamedTextColor.GOLD)));

        List<Component> infoLore = List.of(
                Component.text(warp.effectiveDisplayName(), NamedTextColor.AQUA),
                Component.text("입장료: " + String.format("%,d", warp.price()) + "온", NamedTextColor.YELLOW));
        setButton(4, GuiButton.display(PlayerWarpIcons.icon("playerwarps_gui:default_warpitem", Material.PAPER,
                warp.effectiveDisplayName(), NamedTextColor.AQUA, infoLore)));

        for (int slot : ACCEPT_SLOTS) {
            setButton(slot, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:blank", Material.LIME_STAINED_GLASS_PANE, "수락 (이동)", NamedTextColor.GREEN),
                    event -> {
                        Player player = (Player) event.getWhoClicked();
                        player.closeInventory();
                        PlayerWarpVisit.teleport(ctx, player, warp, true);
                    }));
        }
        for (int slot : DENY_SLOTS) {
            setButton(slot, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:blank", Material.RED_STAINED_GLASS_PANE, "취소", NamedTextColor.RED),
                    event -> event.getWhoClicked().closeInventory()));
        }
    }
}
