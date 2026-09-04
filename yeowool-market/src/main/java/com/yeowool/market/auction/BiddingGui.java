package com.yeowool.market.auction;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /경매}'s Bidding screen (v0id AuctionHouse pack's bidding.yml layout)
 * — adjust the bid amount with +/- buttons before confirming, instead of the
 * old click-to-auto-bid-minimum behaviour. Background {@code yeowool_auction:bidding_bg}.
 */
public final class BiddingGui extends YeowoolGui {

    private static final int SLOT_PREVIEW = 22;
    private static final int SLOT_ACCEPT = 39;
    private static final int SLOT_DENY = 41;
    private static final int[] ADD_SLOTS = {23, 24, 25};
    private static final int[] REMOVE_SLOTS = {19, 20, 21};

    public BiddingGui(AuctionContext ctx, UUID auctionId, long pendingAmount) {
        super(54, AuctionBackgroundImages.title(ctx.backgroundOffsetPx(), "bidding_bg",
                Component.text("입찰", NamedTextColor.GOLD)));

        var listingOpt = ctx.manager().get(auctionId);
        if (listingOpt.isEmpty()) {
            setButton(SLOT_PREVIEW, GuiButton.display(notFoundIcon()));
            setButton(SLOT_DENY, GuiButton.of(denyIcon(), event -> new AuctionGui(ctx, 0, null).open((Player) event.getWhoClicked())));
            return;
        }
        AuctionListing listing = listingOpt.get();
        long increment = ctx.manager().minIncrement(listing);
        long minAmount = listing.minNextBid(increment);
        long amount = Math.max(minAmount, pendingAmount);

        setButton(SLOT_PREVIEW, GuiButton.display(previewIcon(listing, amount)));
        setButton(SLOT_ACCEPT, GuiButton.of(acceptIcon(minAmount, amount), event -> confirm(ctx, auctionId, amount, minAmount, (Player) event.getWhoClicked())));
        setButton(SLOT_DENY, GuiButton.of(denyIcon(), event -> new AuctionGui(ctx, 0, null).open((Player) event.getWhoClicked())));

        long[] increments = ctx.bidIncrements();
        for (int i = 0; i < increments.length && i < ADD_SLOTS.length; i++) {
            long add = increments[i];
            setButton(ADD_SLOTS[i], GuiButton.of(adjustIcon("+" + String.format("%,d", add), NamedTextColor.GREEN), event ->
                    new BiddingGui(ctx, auctionId, amount + add).open((Player) event.getWhoClicked())));
        }
        for (int i = 0; i < increments.length && i < REMOVE_SLOTS.length; i++) {
            long remove = increments[i];
            setButton(REMOVE_SLOTS[i], GuiButton.of(adjustIcon("-" + String.format("%,d", remove), NamedTextColor.RED), event ->
                    new BiddingGui(ctx, auctionId, Math.max(minAmount, amount - remove)).open((Player) event.getWhoClicked())));
        }
    }

    private void confirm(AuctionContext ctx, UUID auctionId, long amount, long minAmount, Player player) {
        var result = ctx.manager().bid(auctionId, player, amount);
        switch (result) {
            case SUCCESS -> {
                ctx.core().sounds().play(player, "success");
                ctx.messages().send(player, "auction.bid-success", Placeholder.unparsed("amount", String.format("%,d", amount)));
                new AuctionGui(ctx, 0, null).open(player);
            }
            case INSUFFICIENT_FUNDS -> {
                ctx.messages().send(player, "auction.insufficient-funds");
                new BiddingGui(ctx, auctionId, amount).open(player);
            }
            case OWN_LISTING -> ctx.messages().send(player, "auction.cannot-bid-own");
            case TOO_LOW -> {
                ctx.messages().send(player, "auction.bid-too-low");
                new BiddingGui(ctx, auctionId, minAmount).open(player);
            }
            case NOT_FOUND -> ctx.messages().send(player, "auction.not-found");
        }
    }

    private ItemStack previewIcon(AuctionListing listing, long amount) {
        ItemStack stack = listing.item().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text("현재 최고 입찰: " + (listing.hasBid() ? String.format("%,d", listing.currentBid()) + listing.currency().displayName() : "없음"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("내 입찰가: " + String.format("%,d", amount) + listing.currency().displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack acceptIcon(long minAmount, long amount) {
        ItemStack stack = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("입찰 확정", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(String.format("%,d", amount) + "에 입찰합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("최소 입찰가: " + String.format("%,d", minAmount), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack denyIcon() {
        ItemStack stack = new ItemStack(Material.RED_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("취소", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack adjustIcon(String label, NamedTextColor color) {
        ItemStack stack = AuctionBackgroundImages.invisibleIcon();
        if (stack == null) {
            stack = new ItemStack(Material.GOLD_NUGGET);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack notFoundIcon() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("경매를 찾을 수 없습니다", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
