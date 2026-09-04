package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/** {@code saved_warps} background — the viewer's favorited warps; click to visit, Shift+click to un-favorite. */
public final class PlayerWarpSavedGui extends YeowoolGui {

    private static final int[] GRID_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};

    public PlayerWarpSavedGui(PlayerWarpContext ctx, Player viewer) {
        super(54, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "saved_warps",
                Component.text("즐겨찾기한 워프", NamedTextColor.GOLD)));

        List<PlayerWarp> favorites = ctx.favorites().favoritesOf(viewer.getUniqueId()).stream()
                .map(key -> {
                    String[] parts = key.split(":", 2);
                    return ctx.warps().get(java.util.UUID.fromString(parts[0]), parts[1]).orElse(null);
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> a.effectiveDisplayName().toLowerCase(Locale.ROOT).compareTo(b.effectiveDisplayName().toLowerCase(Locale.ROOT)))
                .toList();

        for (int i = 0; i < favorites.size() && i < GRID_SLOTS.length; i++) {
            PlayerWarp warp = favorites.get(i);
            setButton(GRID_SLOTS[i], GuiButton.of(buildIcon(warp), event -> {
                Player clicker = (Player) event.getWhoClicked();
                if (event.isShiftClick()) {
                    ctx.favorites().remove(clicker.getUniqueId(), warp.owner(), warp.name());
                    ctx.messages().send(clicker, "playerwarp.favorite-removed", net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", warp.effectiveDisplayName()));
                    new PlayerWarpSavedGui(ctx, clicker).open(clicker);
                    return;
                }
                clicker.closeInventory();
                PlayerWarpVisit.begin(ctx, clicker, warp);
            }));
        }
        if (favorites.isEmpty()) {
            setButton(22, GuiButton.display(PlayerWarpIcons.icon("playerwarps_gui:no_warpsicon", Material.BARRIER, "즐겨찾기한 워프가 없습니다", NamedTextColor.GRAY)));
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
        OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.owner());
        List<Component> lore = List.of(
                Component.text("주인: " + (owner.getName() != null ? owner.getName() : "알 수 없음"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("클릭하여 이동 / Shift+클릭으로 즐겨찾기 해제", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
