package com.yeowool.market.auction;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /경매}'s My Auctions screen (v0id AuctionHouse pack's myauctions.yml
 * layout, same 8-wide grid as {@link AuctionGui}) — the caller's own active
 * listings, click to cancel (only while no one's bid yet — see
 * {@link AuctionManager#cancel}). Back (26) is invisible, no close/empty-state
 * filler. Background {@code yeowool_auction:myauctions_bg}.
 */
public final class MyAuctionsGui extends YeowoolGui {

    private static final int[] GRID_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16,
            18, 19, 20, 21, 22, 23, 24, 25,
            27, 28, 29, 30, 31, 32, 33, 34,
            36, 37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_BACK = 26;
    private static final int SLOT_PREV = 47;
    private static final int SLOT_NEXT = 50;

    public MyAuctionsGui(AuctionContext ctx, Player viewer, int page) {
        super(54, AuctionBackgroundImages.title(ctx.backgroundOffsetPx(), "myauctions_bg",
                Component.text("내 경매", NamedTextColor.GOLD)));

        List<AuctionListing> own = ctx.manager().byOwner(viewer.getUniqueId());
        int from = page * GRID_SLOTS.length;
        int to = Math.min(own.size(), from + GRID_SLOTS.length);

        for (int i = from; i < to; i++) {
            AuctionListing listing = own.get(i);
            setButton(GRID_SLOTS[i - from], GuiButton.of(buildIcon(listing), event -> cancel(ctx, listing, (Player) event.getWhoClicked(), page)));
        }
        if (page > 0) {
            setButton(SLOT_PREV, GuiButton.of(navItem(Material.ARROW, "이전 페이지"), event ->
                    new MyAuctionsGui(ctx, (Player) event.getWhoClicked(), page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(SLOT_BACK, GuiButton.of(invisibleItem("뒤로가기"), event ->
                new AuctionGui(ctx, 0, null).open((Player) event.getWhoClicked())));
        if (to < own.size()) {
            setButton(SLOT_NEXT, GuiButton.of(navItem(Material.ARROW, "다음 페이지"), event ->
                    new MyAuctionsGui(ctx, (Player) event.getWhoClicked(), page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private void cancel(AuctionContext ctx, AuctionListing listing, Player player, int page) {
        var result = ctx.manager().cancel(listing.id(), player);
        switch (result) {
            case SUCCESS -> ctx.messages().send(player, "auction.cancel-success");
            case HAS_BID -> ctx.messages().send(player, "auction.cancel-has-bid");
            case NOT_OWNER, NOT_FOUND -> ctx.messages().send(player, "auction.not-found");
        }
        new MyAuctionsGui(ctx, player, page).open(player);
    }

    private ItemStack buildIcon(AuctionListing listing) {
        ItemStack stack = listing.item().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text(listing.hasBid()
                ? "현재 입찰가: " + String.format("%,d", listing.currentBid()) + listing.currency().displayName()
                : "시작가: " + String.format("%,d", listing.startingBid()) + listing.currency().displayName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        if (listing.hasBuyNow()) {
            lore.add(Component.text("즉시구매가: " + String.format("%,d", listing.buyNowPrice()) + listing.currency().displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        long remaining = Math.max(0, listing.endAtMillis() - System.currentTimeMillis());
        lore.add(Component.text("남은 시간: " + DurationFormat.humanize(remaining), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add((listing.hasBid()
                ? Component.text("입찰이 들어와서 취소할 수 없습니다.", NamedTextColor.DARK_GRAY)
                : Component.text("클릭하여 취소", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    /** {@code yeowool_auction:auction_invisible} — the label is still shown as a tooltip, the icon itself is invisible. */
    private ItemStack invisibleItem(String name) {
        ItemStack stack = AuctionBackgroundImages.invisibleIcon();
        if (stack == null) {
            stack = new ItemStack(Material.GLASS_PANE);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
