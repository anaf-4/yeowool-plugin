package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.function.Consumer;

/** {@code warp_statustitle} background — pick OPENED (browsable by everyone) or CLOSED (hidden from the browse list, owner can still visit it). */
public final class PlayerWarpStatusGui extends YeowoolGui {

    public PlayerWarpStatusGui(PlayerWarpContext ctx, PlayerWarp.Status current, Consumer<PlayerWarp.Status> onSelect) {
        super(27, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "warp_statustitle",
                Component.text("공개 상태 설정", NamedTextColor.GOLD)));

        setButton(11, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_accessibility", Material.LIME_STAINED_GLASS_PANE,
                        "공개" + (current == PlayerWarp.Status.OPENED ? " (현재)" : ""), NamedTextColor.GREEN),
                event -> onSelect.accept(PlayerWarp.Status.OPENED)));
        setButton(15, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_accessibility", Material.RED_STAINED_GLASS_PANE,
                        "비공개" + (current == PlayerWarp.Status.CLOSED ? " (현재)" : ""), NamedTextColor.RED),
                event -> onSelect.accept(PlayerWarp.Status.CLOSED)));
    }
}
