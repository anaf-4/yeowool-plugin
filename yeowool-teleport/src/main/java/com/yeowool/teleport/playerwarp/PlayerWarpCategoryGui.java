package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.function.Consumer;

/**
 * {@code warp_categorytitle} background — reused both to filter the browse
 * list by category and (from {@link PlayerWarpEditGui}) to set a specific
 * warp's category, distinguished only by what {@code onSelect} does with the
 * chosen id ({@code null} = "전체"/no category, from {@link PlayerWarpConfig#categories()}).
 */
public final class PlayerWarpCategoryGui extends YeowoolGui {

    public PlayerWarpCategoryGui(PlayerWarpContext ctx, Consumer<String> onSelect) {
        super(45, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "warp_categorytitle",
                Component.text("카테고리 선택", NamedTextColor.GOLD)));

        setButton(10, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:all_warpsicon", Material.NETHER_STAR, "전체", NamedTextColor.WHITE), event -> {
            onSelect.accept(null);
        }));

        int slot = 11;
        for (var category : ctx.config().categories()) {
            if (slot > 34) {
                break;
            }
            setButton(slot, GuiButton.of(PlayerWarpIcons.icon(category.iconId(), Material.CHEST, category.displayName(), NamedTextColor.AQUA), event -> {
                onSelect.accept(category.id());
            }));
            slot++;
        }

        setButton(40, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_home", Material.BARRIER, "취소", NamedTextColor.GRAY), event ->
                event.getWhoClicked().closeInventory()));
    }
}
