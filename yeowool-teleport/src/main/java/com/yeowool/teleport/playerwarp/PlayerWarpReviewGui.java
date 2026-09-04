package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code review_warp} background ("RATE WARP") — 5 star buttons, one rating per player per warp (re-rating overwrites the old one). */
public final class PlayerWarpReviewGui extends YeowoolGui {

    private static final int[] STAR_SLOTS = {11, 12, 13, 14, 15};

    public PlayerWarpReviewGui(PlayerWarpContext ctx, PlayerWarp warp) {
        super(45, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "review_warp",
                Component.text("워프 평가: " + warp.effectiveDisplayName(), NamedTextColor.GOLD)));

        for (int i = 0; i < STAR_SLOTS.length; i++) {
            int stars = i + 1;
            setButton(STAR_SLOTS[i], GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:rate_iconwarp", Material.NETHER_STAR,
                    "★".repeat(stars) + "☆".repeat(5 - stars), NamedTextColor.YELLOW, List.of(Component.text("클릭하여 " + stars + "점 평가", NamedTextColor.GRAY))),
                    event -> {
                        Player player = (Player) event.getWhoClicked();
                        ctx.ratings().rate(warp.owner(), warp.name(), player.getUniqueId(), stars);
                        ctx.messages().send(player, "playerwarp.rating-success",
                                Placeholder.unparsed("name", warp.effectiveDisplayName()), Placeholder.unparsed("stars", String.valueOf(stars)));
                        player.closeInventory();
                    }));
        }

        setButton(31, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_home", Material.BARRIER, "닫기", NamedTextColor.GRAY), event ->
                event.getWhoClicked().closeInventory()));
    }
}
