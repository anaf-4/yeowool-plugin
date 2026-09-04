package com.yeowool.market.auction;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /경매} — browse live auctions. Layout follows the reference mockup
 * (v0id AuctionHouse pack): an 8-wide item grid (column 8 reserved for the
 * nav rail: 내 경매/우편함/정렬/검색/카테고리 top-to-bottom, all invisible —
 * the graphics are painted into the background itself), pagination at 47/50.
 * No close/empty-state filler — the real plugin doesn't have those either.
 * Click opens {@link BiddingGui}; shift-click (if a buy-now price is set)
 * opens {@link ConfirmPurchaseGui} instead — neither acts instantly, both
 * confirm on their own screen first. Background {@code yeowool_auction:auctionhouse_bg}.
 */
public final class AuctionGui extends YeowoolGui {

    private static final int[] GRID_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16,
            18, 19, 20, 21, 22, 23, 24, 25,
            27, 28, 29, 30, 31, 32, 33, 34,
            36, 37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_MY_AUCTIONS = 8;
    private static final int SLOT_MAILBOX = 17;
    private static final int SLOT_SORT = 26;
    private static final int SLOT_SEARCH_HINT = 35;
    private static final int SLOT_CATEGORY = 44;
    private static final int SLOT_PREV = 47;
    private static final int SLOT_NEXT = 50;

    public enum Sort { ENDING_SOON, NEWEST, PRICE_LOW, PRICE_HIGH }

    public enum Category {
        ALL("전체"), WEAPON("무기"), TOOL("도구"), ARMOR("방어구"), BLOCK("블록"), MISC("기타");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        boolean matches(Material material) {
            if (this == ALL) {
                return true;
            }
            String name = material.name();
            boolean isWeapon = name.endsWith("_SWORD") || name.endsWith("_AXE") && !name.contains("PICKAXE")
                    || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT");
            boolean isTool = !isWeapon && (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                    || name.equals("SHEARS") || name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL"));
            boolean isArmor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS") || name.equals("SHIELD") || name.equals("ELYTRA") || name.equals("TURTLE_HELMET");
            boolean isBlock = material.isBlock();
            return switch (this) {
                case WEAPON -> isWeapon;
                case TOOL -> isTool;
                case ARMOR -> isArmor;
                case BLOCK -> !isWeapon && !isTool && !isArmor && isBlock;
                case MISC -> !isWeapon && !isTool && !isArmor && !isBlock;
                default -> true;
            };
        }
    }

    public AuctionGui(AuctionContext ctx, int page, String keyword) {
        this(ctx, page, keyword, Sort.ENDING_SOON, Category.ALL);
    }

    public AuctionGui(AuctionContext ctx, int page, String keyword, Sort sort, Category category) {
        super(54, AuctionBackgroundImages.title(ctx.backgroundOffsetPx(), "auctionhouse_bg",
                Component.text("경매장" + (keyword != null ? " - 검색: " + keyword : "") + " (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA)));

        List<AuctionListing> all = keyword == null
                ? new ArrayList<>(ctx.manager().all())
                : ctx.manager().all().stream().filter(listing -> matchesKeyword(listing, keyword)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        all.removeIf(listing -> !category.matches(listing.item().getType()));
        sortInPlace(all, sort);

        int from = page * GRID_SLOTS.length;
        int to = Math.min(all.size(), from + GRID_SLOTS.length);

        for (int i = from; i < to; i++) {
            AuctionListing listing = all.get(i);
            setButton(GRID_SLOTS[i - from], GuiButton.of(buildIcon(listing), event -> {
                Player player = (Player) event.getWhoClicked();
                boolean buyNowClick = (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) && listing.hasBuyNow();
                if (buyNowClick) {
                    new ConfirmPurchaseGui(ctx, listing.id()).open(player);
                } else {
                    new BiddingGui(ctx, listing.id(), 0).open(player);
                }
            }));
        }

        if (page > 0) {
            setButton(SLOT_PREV, GuiButton.of(navItem(Material.ARROW, "이전 페이지"), event ->
                    new AuctionGui(ctx, page - 1, keyword, sort, category).open((Player) event.getWhoClicked())));
        }
        setButton(SLOT_MY_AUCTIONS, GuiButton.of(invisibleItem("내 경매"), event ->
                new MyAuctionsGui(ctx, (Player) event.getWhoClicked(), 0).open((Player) event.getWhoClicked())));
        setButton(SLOT_MAILBOX, GuiButton.of(invisibleItem("우편함 (낙찰/유찰된 아이템 수령)"), event -> {
            Player player = (Player) event.getWhoClicked();
            ctx.core().mailbox().loadPending(player.getUniqueId()).thenAccept(entries ->
                    Bukkit.getScheduler().runTask(ctx.plugin(), () ->
                            new com.yeowool.core.mailbox.MailboxGui(ctx.plugin(), ctx.core().mailbox(), ctx.messages(), entries, 0).open(player)));
        }));
        setButton(SLOT_SORT, GuiButton.of(invisibleItem("정렬: " + sortLabel(sort)), event ->
                new AuctionGui(ctx, 0, keyword, nextSort(sort), category).open((Player) event.getWhoClicked())));
        setButton(SLOT_SEARCH_HINT, GuiButton.display(invisibleItem("검색: /경매 검색 <키워드>")));
        setButton(SLOT_CATEGORY, GuiButton.of(invisibleItem("카테고리: " + category.label), event ->
                new AuctionGui(ctx, 0, keyword, sort, nextCategory(category)).open((Player) event.getWhoClicked())));
        if (to < all.size()) {
            setButton(SLOT_NEXT, GuiButton.of(navItem(Material.ARROW, "다음 페이지"), event ->
                    new AuctionGui(ctx, page + 1, keyword, sort, category).open((Player) event.getWhoClicked())));
        }
    }

    private void sortInPlace(List<AuctionListing> list, Sort sort) {
        Comparator<AuctionListing> comparator = switch (sort) {
            case ENDING_SOON -> Comparator.comparingLong(AuctionListing::endAtMillis);
            case NEWEST -> Comparator.comparingLong(AuctionListing::createdAt).reversed();
            case PRICE_LOW -> Comparator.comparingLong(l -> l.hasBid() ? l.currentBid() : l.startingBid());
            case PRICE_HIGH -> Comparator.comparingLong((AuctionListing l) -> l.hasBid() ? l.currentBid() : l.startingBid()).reversed();
        };
        list.sort(comparator);
    }

    private Sort nextSort(Sort current) {
        Sort[] values = Sort.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private Category nextCategory(Category current) {
        Category[] values = Category.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private String sortLabel(Sort sort) {
        return switch (sort) {
            case ENDING_SOON -> "마감임박순";
            case NEWEST -> "최신순";
            case PRICE_LOW -> "가격낮은순";
            case PRICE_HIGH -> "가격높은순";
        };
    }

    private boolean matchesKeyword(AuctionListing listing, String keyword) {
        String lower = keyword.toLowerCase();
        ItemStack item = listing.item();
        if (item.getType().name().toLowerCase().replace('_', ' ').contains(lower)) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            return plain.toLowerCase().contains(lower);
        }
        return false;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    /** {@code yeowool_auction:auction_invisible} — the label is still shown as a tooltip, the icon itself is invisible (graphics are painted into the background). */
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

    private ItemStack buildIcon(AuctionListing listing) {
        ItemStack stack = listing.item().clone();
        ItemMeta meta = stack.getItemMeta();
        String sellerName = Bukkit.getOfflinePlayer(listing.seller()).getName();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text("판매자: " + (sellerName != null ? sellerName : "알 수 없음"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(listing.hasBid()
                ? "현재 입찰가: " + String.format("%,d", listing.currentBid()) + listing.currency().displayName()
                : "시작가: " + String.format("%,d", listing.startingBid()) + listing.currency().displayName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        if (listing.hasBuyNow()) {
            lore.add(Component.text("즉시구매가: " + String.format("%,d", listing.buyNowPrice()) + listing.currency().displayName() + " (Shift+클릭)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        long remaining = Math.max(0, listing.endAtMillis() - System.currentTimeMillis());
        lore.add(Component.text("남은 시간: " + DurationFormat.humanize(remaining), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 입찰", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
