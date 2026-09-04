package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.teleport.model.PlayerWarp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The full warp grid, reached from {@link PlayerWarpHubGui} — styled with
 * the "PlayerWarps GUI" pack's {@code warps_title} background (distinct from
 * the hub's {@code playerwarps} background). Grid fills slots 0-44, nav row
 * at 45-53. Click a warp to visit ({@link PlayerWarpConfirmGui} first if it
 * charges admission), Shift+click to toggle favorite, right-click to rate.
 */
public final class PlayerWarpBrowseGui extends YeowoolGui {

    public enum SortMode { ALPHABETICAL, VISITS, LATEST, RATING }

    private static final int PAGE_SIZE = 45;
    private static final int[] GRID_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};

    public PlayerWarpBrowseGui(PlayerWarpContext ctx, PlayerWarpTextInput textInput, int page, String categoryFilter, String searchQuery, SortMode sort) {
        super(54, PlayerWarpBackgroundImages.title(ctx.config().backgroundOffsetPx(), "warps_title",
                Component.text("플레이어 워프 (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA)));

        List<PlayerWarp> filtered = ctx.warps().all().stream()
                .filter(w -> w.status() == PlayerWarp.Status.OPENED)
                .filter(w -> categoryFilter == null || categoryFilter.equals(w.category()))
                .filter(w -> searchQuery == null || searchQuery.isBlank()
                        || w.effectiveDisplayName().toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT)))
                .sorted(comparatorFor(ctx, sort))
                .toList();

        int from = page * PAGE_SIZE;
        int to = Math.min(filtered.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            PlayerWarp warp = filtered.get(i);
            setButton(GRID_SLOTS[i - from], GuiButton.of(buildIcon(ctx, warp), event -> {
                Player player = (Player) event.getWhoClicked();
                if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    ctx.favorites().toggle(player.getUniqueId(), warp.owner(), warp.name());
                    ctx.messages().send(player, ctx.favorites().isFavorite(player.getUniqueId(), warp.owner(), warp.name())
                            ? "playerwarp.favorite-added" : "playerwarp.favorite-removed",
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", warp.effectiveDisplayName()));
                    new PlayerWarpBrowseGui(ctx, textInput, page, categoryFilter, searchQuery, sort).open(player);
                    return;
                }
                if (event.getClick() == ClickType.RIGHT) {
                    player.closeInventory();
                    new PlayerWarpReviewGui(ctx, warp).open(player);
                    return;
                }
                player.closeInventory();
                PlayerWarpVisit.begin(ctx, player, warp);
            }));
        }
        if (filtered.isEmpty()) {
            setButton(47, GuiButton.display(PlayerWarpIcons.icon("playerwarps_gui:no_warpsicon", Material.BARRIER,
                    "조건에 맞는 플레이어 워프가 없습니다", NamedTextColor.GRAY)));
        }

        boolean hasPrev = page > 0;
        setButton(45, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:left_warps", Material.ARROW, "이전 페이지", NamedTextColor.GRAY), event -> {
            if (hasPrev) {
                new PlayerWarpBrowseGui(ctx, textInput, page - 1, categoryFilter, searchQuery, sort).open((Player) event.getWhoClicked());
            }
        }));
        setButton(46, GuiButton.of(sortIcon(sort), event ->
                new PlayerWarpBrowseGui(ctx, textInput, 0, categoryFilter, searchQuery, next(sort)).open((Player) event.getWhoClicked())));
        setButton(48, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_home", Material.BARRIER, "허브로", NamedTextColor.GRAY), event -> {
            Player clicker = (Player) event.getWhoClicked();
            new PlayerWarpHubGui(ctx, textInput).open(clicker);
        }));
        setButton(49, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:pwarp_settings", Material.WRITABLE_BOOK, "내 워프", NamedTextColor.GOLD), event -> {
            Player clicker = (Player) event.getWhoClicked();
            new PlayerWarpMyGui(ctx, textInput, clicker).open(clicker);
        }));
        setButton(50, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:warps_save", Material.NETHER_STAR, "즐겨찾기", NamedTextColor.LIGHT_PURPLE), event -> {
            Player clicker = (Player) event.getWhoClicked();
            new PlayerWarpSavedGui(ctx, clicker).open(clicker);
        }));
        setButton(52, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:search_warpicon", Material.COMPASS, "검색", NamedTextColor.AQUA), event ->
                textInput.openSearch((Player) event.getWhoClicked())));
        boolean hasNext = to < filtered.size();
        setButton(53, GuiButton.of(PlayerWarpIcons.icon("playerwarps_gui:right_warps", Material.ARROW, "다음 페이지", NamedTextColor.GRAY), event -> {
            if (hasNext) {
                new PlayerWarpBrowseGui(ctx, textInput, page + 1, categoryFilter, searchQuery, sort).open((Player) event.getWhoClicked());
            }
        }));
    }

    private static SortMode next(SortMode current) {
        SortMode[] values = SortMode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static Comparator<PlayerWarp> comparatorFor(PlayerWarpContext ctx, SortMode sort) {
        return switch (sort) {
            case ALPHABETICAL -> Comparator.comparing(w -> w.effectiveDisplayName().toLowerCase(Locale.ROOT));
            case VISITS -> Comparator.comparingLong(PlayerWarp::visits).reversed();
            case LATEST -> Comparator.comparingLong(PlayerWarp::createdAt).reversed();
            case RATING -> Comparator.comparingDouble((PlayerWarp w) -> ctx.ratings().averageOf(w.owner(), w.name())).reversed();
        };
    }

    private ItemStack sortIcon(SortMode sort) {
        String label = switch (sort) {
            case ALPHABETICAL -> "정렬: 이름순";
            case VISITS -> "정렬: 방문순";
            case LATEST -> "정렬: 최신순";
            case RATING -> "정렬: 평점순";
        };
        return PlayerWarpIcons.icon("playerwarps_gui:sort_warps", Material.HOPPER, label, NamedTextColor.YELLOW,
                List.of(Component.text("클릭하여 정렬 방식 변경", NamedTextColor.GRAY)));
    }

    private ItemStack buildIcon(PlayerWarpContext ctx, PlayerWarp warp) {
        String iconId = warp.previewItemId() != null && warp.previewItemId().contains(":") ? warp.previewItemId() : null;
        ItemStack stack = iconId != null ? PlayerWarpIcons.resolveCustom(iconId) : null;
        if (stack == null) {
            Material material = parseMaterial(warp.previewItemId());
            stack = new ItemStack(material != null ? material : Material.PAPER);
            if (material == null) {
                ItemStack custom = PlayerWarpIcons.resolveCustom("playerwarps_gui:default_warpitem");
                if (custom != null) {
                    stack = custom;
                }
            }
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(warp.effectiveDisplayName(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.owner());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("주인: " + (owner.getName() != null ? owner.getName() : "알 수 없음"), NamedTextColor.GRAY));
        if (!"none".equals(warp.category())) {
            lore.add(Component.text("카테고리: " + warp.category(), NamedTextColor.GRAY));
        }
        if (warp.description() != null && !warp.description().isBlank()) {
            lore.add(Component.text(warp.description(), NamedTextColor.WHITE));
        }
        double avg = ctx.ratings().averageOf(warp.owner(), warp.name());
        int count = ctx.ratings().countOf(warp.owner(), warp.name());
        lore.add(Component.text((count > 0 ? String.format("★ %.1f (%d명)", avg, count) : "★ 평가 없음"), NamedTextColor.GOLD));
        lore.add(Component.text("방문: " + warp.visits() + "회", NamedTextColor.GRAY));
        if (warp.price() > 0) {
            lore.add(Component.text("입장료: " + String.format("%,d", warp.price()) + "온", NamedTextColor.YELLOW));
        }
        lore.add(Component.text("클릭: 이동 / Shift+클릭: 즐겨찾기 / 우클릭: 평가", NamedTextColor.GREEN));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material parseMaterial(String id) {
        if (id == null || id.contains(":")) {
            return null;
        }
        try {
            return Material.valueOf(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
