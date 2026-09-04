package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import com.yeowool.teleport.util.PlayerWarpCurrency;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code edit_warp} background — owner-only settings menu. Slot positions
 * (11 price / 12 category / 13 display-name / 14 preview-item / 15
 * description / 20 remove / 21 rename / 22 accessibility / 23 relocate / 24
 * owner / 31 overview) come straight from the real PlayerWarps plugin's own
 * {@code config.yml}.
 */
public final class PlayerWarpEditGui extends YeowoolGui {

    public PlayerWarpEditGui(PlayerWarpContext ctx, PlayerWarp warp, PlayerWarpTextInput textInput) {
        super(54, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "edit_warp",
                Component.text("워프 설정: " + warp.effectiveDisplayName(), NamedTextColor.GOLD)));

        setButton(31, GuiButton.display(overviewIcon(warp)));

        setButton(11, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:set_price", Material.GOLD_NUGGET, "입장료 설정", NamedTextColor.YELLOW,
                        List.of(Component.text("현재: " + String.format("%,d", warp.price()) + "온", NamedTextColor.GRAY))),
                event -> textInput.openPrice((Player) event.getWhoClicked(), warp)));

        setButton(12, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:set_categoryitem", Material.CHEST, "카테고리 설정", NamedTextColor.AQUA,
                        List.of(Component.text("현재: " + warp.category(), NamedTextColor.GRAY))),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    new PlayerWarpCategoryGui(ctx, selected -> {
                        PlayerWarp updated = warp.withCategory(selected == null ? "none" : selected);
                        ctx.warps().put(updated);
                        ctx.messages().send(player, "playerwarp.edit-success");
                        new PlayerWarpEditGui(ctx, updated, textInput).open(player);
                    }).open(player);
                }));

        setButton(13, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_displayname", Material.NAME_TAG, "표시 이름 변경", NamedTextColor.AQUA),
                event -> textInput.openDisplayName((Player) event.getWhoClicked(), warp)));

        setButton(14, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_previewitem", Material.ITEM_FRAME, "미리보기 아이템 변경", NamedTextColor.AQUA,
                        List.of(Component.text("손에 든 아이템으로 변경됩니다", NamedTextColor.GRAY))),
                event -> changePreviewItem(ctx, (Player) event.getWhoClicked(), warp, textInput)));

        setButton(15, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_description", Material.WRITABLE_BOOK, "설명 변경", NamedTextColor.AQUA),
                event -> textInput.openDescription((Player) event.getWhoClicked(), warp)));

        setButton(20, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:remove_warpitem", Material.BARRIER, "워프 삭제", NamedTextColor.RED,
                        List.of(Component.text("환불: " + String.format("%,d", ctx.config().deleteRefund()) + "온", NamedTextColor.GRAY))),
                event -> removeWarp(ctx, (Player) event.getWhoClicked(), warp)));

        setButton(21, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_displayname", Material.ANVIL, "이름 변경", NamedTextColor.AQUA,
                        List.of(Component.text("수수료: " + String.format("%,d", ctx.config().renameFee()) + "온", NamedTextColor.GRAY))),
                event -> textInput.openRename((Player) event.getWhoClicked(), warp)));

        setButton(22, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_accessibility", Material.LEVER, "공개 상태 설정", NamedTextColor.AQUA,
                        List.of(Component.text("현재: " + (warp.status() == PlayerWarp.Status.OPENED ? "공개" : "비공개"), NamedTextColor.GRAY))),
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    new PlayerWarpStatusGui(ctx, warp.status(), status -> {
                        if (!chargeFee(ctx, player, ctx.config().setAccessibilityFee())) {
                            return;
                        }
                        PlayerWarp updated = warp.withStatus(status);
                        ctx.warps().put(updated);
                        ctx.messages().send(player, "playerwarp.edit-success");
                        player.closeInventory();
                        new PlayerWarpEditGui(ctx, updated, textInput).open(player);
                    }).open(player);
                }));

        setButton(23, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:default_warpitem", Material.COMPASS, "현재 위치로 이전", NamedTextColor.AQUA,
                        List.of(Component.text("수수료: " + String.format("%,d", ctx.config().relocateFee()) + "온", NamedTextColor.GRAY))),
                event -> relocateWarp(ctx, (Player) event.getWhoClicked(), warp, textInput)));

        setButton(24, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:change_ownericon", Material.PLAYER_HEAD, "소유권 이전", NamedTextColor.AQUA,
                        List.of(Component.text("수수료: " + String.format("%,d", ctx.config().transferOwnershipFee()) + "온", NamedTextColor.GRAY))),
                event -> textInput.openTransferTarget((Player) event.getWhoClicked(), warp)));

        setButton(49, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_home", Material.BARRIER, "닫기", NamedTextColor.GRAY),
                event -> event.getWhoClicked().closeInventory()));
    }

    private ItemStack overviewIcon(PlayerWarp warp) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(warp.effectiveDisplayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = List.of(
                Component.text("월드: " + warp.world(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(String.format("좌표: %.0f, %.0f, %.0f", warp.x(), warp.y(), warp.z()), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("방문: " + warp.visits() + "회", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private void changePreviewItem(PlayerWarpContext ctx, Player player, PlayerWarp warp, PlayerWarpTextInput textInput) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            ctx.messages().send(player, "playerwarp.hand-empty");
            return;
        }
        if (ctx.config().bannedPreviewItems().contains(hand.getType())) {
            ctx.messages().send(player, "playerwarp.preview-item-banned");
            return;
        }
        if (!chargeFee(ctx, player, ctx.config().setPreviewItemFee())) {
            return;
        }
        String id;
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.byItemStack(hand);
            id = custom != null ? custom.getNamespacedID() : hand.getType().name();
        } else {
            id = hand.getType().name();
        }
        PlayerWarp updated = warp.withPreviewItemId(id);
        ctx.warps().put(updated);
        ctx.messages().send(player, "playerwarp.edit-success");
        new PlayerWarpEditGui(ctx, updated, textInput).open(player);
    }

    private void removeWarp(PlayerWarpContext ctx, Player player, PlayerWarp warp) {
        if (!ctx.warps().delete(warp.owner(), warp.name())) {
            return;
        }
        PlayerWarpCurrency.grant(ctx, player.getUniqueId(), ctx.config().deleteRefund(), "플레이어 워프 삭제 환불 (" + warp.name() + ")");
        ctx.messages().send(player, "playerwarp.delete-success", Placeholder.unparsed("name", warp.name()));
        player.closeInventory();
    }

    private void relocateWarp(PlayerWarpContext ctx, Player player, PlayerWarp warp, PlayerWarpTextInput textInput) {
        if (!warp.owner().equals(player.getUniqueId())) {
            return;
        }
        if (!chargeFee(ctx, player, ctx.config().relocateFee())) {
            return;
        }
        PlayerWarp updated = warp.withLocation(player.getLocation());
        ctx.warps().put(updated);
        ctx.messages().send(player, "playerwarp.relocate-success", Placeholder.unparsed("name", warp.name()));
        new PlayerWarpEditGui(ctx, updated, textInput).open(player);
    }

    private boolean chargeFee(PlayerWarpContext ctx, Player player, long fee) {
        if (fee <= 0) {
            return true;
        }
        if (!PlayerWarpCurrency.has(ctx, player.getUniqueId(), fee)) {
            ctx.messages().send(player, "playerwarp.insufficient-funds", Placeholder.unparsed("cost", String.format("%,d", fee)));
            return false;
        }
        PlayerWarpCurrency.charge(ctx, player.getUniqueId(), fee, "플레이어 워프 편집 수수료");
        return true;
    }
}
