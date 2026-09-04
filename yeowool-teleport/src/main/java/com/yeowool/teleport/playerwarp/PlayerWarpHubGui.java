package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /플레이어워프}'s entry screen — {@code playerwarps} background (the
 * one with "PLAYERWARPS" baked into the title art). Category shortcuts +
 * quick nav into the full grid ({@link PlayerWarpBrowseGui}, {@code warps_title}
 * background), my warps, and favorites. Slot layout is exactly as specified.
 */
public final class PlayerWarpHubGui extends YeowoolGui {

    public PlayerWarpHubGui(PlayerWarpContext ctx, PlayerWarpTextInput textInput) {
        super(54, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "playerwarps",
                Component.text("플레이어 워프", NamedTextColor.DARK_AQUA)));

        setButton(20, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:events_warpicon", Material.NETHER_STAR, "이벤트", NamedTextColor.AQUA), event ->
                openGrid(ctx, textInput, (Player) event.getWhoClicked(), "events")));
        setButton(21, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:building_warpicon", Material.BRICKS, "건축", NamedTextColor.AQUA), event ->
                openGrid(ctx, textInput, (Player) event.getWhoClicked(), "building")));
        setButton(22, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_shop", Material.CHEST, "전체 보기", NamedTextColor.GOLD), event ->
                openGrid(ctx, textInput, (Player) event.getWhoClicked(), null)));
        setButton(23, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:farm_warpicon", Material.WHEAT, "농장", NamedTextColor.AQUA), event ->
                openGrid(ctx, textInput, (Player) event.getWhoClicked(), "farm")));
        setButton(24, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_ownericon", Material.CHEST, "전체 보기", NamedTextColor.GOLD), event ->
                openGrid(ctx, textInput, (Player) event.getWhoClicked(), null)));
        setButton(31, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:all_warpsicon", Material.NETHER_STAR, "전체 보기", NamedTextColor.GOLD), event ->
                openGrid(ctx, textInput, (Player) event.getWhoClicked(), null)));

        setButton(48, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_home", Material.BARRIER, "닫기", NamedTextColor.GRAY), event ->
                event.getWhoClicked().closeInventory()));
        setButton(49, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_settings", Material.WRITABLE_BOOK, "내 워프", NamedTextColor.GOLD), event -> {
            Player clicker = (Player) event.getWhoClicked();
            new PlayerWarpMyGui(ctx, textInput, clicker).open(clicker);
        }));
        setButton(50, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:warps_save", Material.NETHER_STAR, "즐겨찾기", NamedTextColor.LIGHT_PURPLE), event -> {
            Player clicker = (Player) event.getWhoClicked();
            new PlayerWarpSavedGui(ctx, clicker).open(clicker);
        }));
    }

    private void openGrid(PlayerWarpContext ctx, PlayerWarpTextInput textInput, Player player, String category) {
        new PlayerWarpBrowseGui(ctx, textInput, 0, category, null, PlayerWarpBrowseGui.SortMode.ALPHABETICAL).open(player);
    }
}
