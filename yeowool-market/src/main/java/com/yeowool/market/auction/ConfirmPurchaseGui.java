package com.yeowool.market.auction;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /경매}'s buy-now confirm screen (v0id AuctionHouse pack's
 * confirm.yml layout) — a listing is always bought/sold as one whole
 * {@link ItemStack} (no partial-stack amount picker, unlike the reference —
 * our auctions were never designed to be split). Background
 * {@code yeowool_auction:confirmpurchase_bg}.
 */
public final class ConfirmPurchaseGui extends YeowoolGui {

    private static final int SLOT_PREVIEW = 22;
    private static final int SLOT_ACCEPT = 39;
    private static final int SLOT_DENY = 41;

    public ConfirmPurchaseGui(AuctionContext ctx, UUID auctionId) {
        super(54, AuctionBackgroundImages.title(ctx.backgroundOffsetPx(), "confirmpurchase_bg",
                Component.text("구매 확인", NamedTextColor.GOLD)));

        var listingOpt = ctx.manager().get(auctionId);
        if (listingOpt.isEmpty() || !listingOpt.get().hasBuyNow()) {
            setButton(SLOT_PREVIEW, GuiButton.display(notFoundIcon()));
            setButton(SLOT_DENY, GuiButton.of(denyIcon(), event -> new AuctionGui(ctx, 0, null).open((Player) event.getWhoClicked())));
            return;
        }
        AuctionListing listing = listingOpt.get();

        setButton(SLOT_PREVIEW, GuiButton.display(previewIcon(listing)));
        setButton(SLOT_ACCEPT, GuiButton.of(acceptIcon(listing), event -> confirm(ctx, auctionId, (Player) event.getWhoClicked())));
        setButton(SLOT_DENY, GuiButton.of(denyIcon(), event -> new AuctionGui(ctx, 0, null).open((Player) event.getWhoClicked())));
    }

    private void confirm(AuctionContext ctx, UUID auctionId, Player player) {
        var result = ctx.manager().buyNow(auctionId, player, ctx.feePercent());
        switch (result) {
            case SUCCESS -> {
                ctx.core().sounds().play(player, "success");
                ctx.messages().send(player, "auction.buy-now-success");
                new AuctionGui(ctx, 0, null).open(player);
            }
            case INSUFFICIENT_FUNDS -> {
                ctx.messages().send(player, "auction.insufficient-funds");
                new AuctionGui(ctx, 0, null).open(player);
            }
            case OWN_LISTING -> ctx.messages().send(player, "auction.cannot-bid-own");
            case NOT_FOUND, TOO_LOW -> {
                ctx.messages().send(player, "auction.not-found");
                new AuctionGui(ctx, 0, null).open(player);
            }
        }
    }

    private ItemStack previewIcon(AuctionListing listing) {
        ItemStack stack = listing.item().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text("즉시구매가: " + String.format("%,d", listing.buyNowPrice()) + listing.currency().displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack acceptIcon(AuctionListing listing) {
        ItemStack stack = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("구매 확정", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(String.format("%,d", listing.buyNowPrice()) + listing.currency().displayName() + "에 즉시구매합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
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

    private ItemStack notFoundIcon() {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("구매할 수 없는 경매입니다", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
