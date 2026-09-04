package com.yeowool.community.menu;

import com.yeowool.community.battlepass.BattlePassPortalGui;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /메뉴} (and Shift+F) hub: the 9 category buttons in a single row (0-8). Every category
 * opens a {@link MenuCategoryGui} except 배틀패스 (opens {@link BattlePassPortalGui} directly -
 * it's already a hub of its own) and 상점 (dispatches {@code /상점이동}, a teleport to the shop NPC
 * rather than a GUI).
 */
public final class MenuHubGui extends YeowoolGui {

    private static final int SLOT_ECONOMY_CATEGORY = 0;
    private static final int SLOT_LIFE_CATEGORY = 1;
    private static final int SLOT_MARKET_CATEGORY = 2;
    private static final int SLOT_LAND_CATEGORY = 3;
    private static final int SLOT_TELEPORT_CATEGORY = 4;
    private static final int SLOT_COMMUNITY_CATEGORY = 5;
    private static final int SLOT_QUEST_CATEGORY = 6;
    private static final int SLOT_ENHANCE_CATEGORY = 7;
    private static final int SLOT_ETC_CATEGORY = 8;

    public MenuHubGui(MenuContext ctx, Player viewer) {
        super(54, MenuBackgroundImages.title(ctx.config().hubBackgroundOffsetPx(), "menu_bg_hub_v2", Component.text("메뉴", NamedTextColor.GOLD)));

        // 5개는 이 서버 ItemsAdder에 이미 깔려있는 다른 팩의 전용 아이콘(/iagive로 실제 등록 확인함),
        // 나머지 4개는 어울리는 전용 아이콘을 못 찾아서 바닐라로 대체.
        setButton(SLOT_ECONOMY_CATEGORY, GuiButton.of(categoryIcon("경제", null, Material.GOLD_INGOT), event -> openCategory(ctx, event, this::economyCategory)));
        setButton(SLOT_LIFE_CATEGORY, GuiButton.of(categoryIcon("생활", "medival_jobs:medival_jobs_farmer", Material.DIAMOND_HOE), event -> openCategory(ctx, event, this::lifeCategory)));
        setButton(SLOT_MARKET_CATEGORY, GuiButton.of(categoryIcon("상점/시장", null, Material.EMERALD), event -> openCategory(ctx, event, this::marketCategory)));
        setButton(SLOT_LAND_CATEGORY, GuiButton.of(categoryIcon("토지", null, Material.GRASS_BLOCK), event -> openCategory(ctx, event, this::landCategory)));
        setButton(SLOT_TELEPORT_CATEGORY, GuiButton.of(categoryIcon("텔레포트", "playerwarps_gui:pwarp_home", Material.ENDER_PEARL), event -> openCategory(ctx, event, this::teleportCategory)));
        setButton(SLOT_COMMUNITY_CATEGORY, GuiButton.of(categoryIcon("커뮤니티", null, Material.PLAYER_HEAD), event -> openCategory(ctx, event, this::communityCategory)));
        setButton(SLOT_QUEST_CATEGORY, GuiButton.of(categoryIcon("퀘스트/이벤트", "daily_quest:reward", Material.WRITTEN_BOOK), event -> openCategory(ctx, event, this::questCategory)));
        setButton(SLOT_ENHANCE_CATEGORY, GuiButton.of(categoryIcon("강화", "yeowool_enhance:book_simple", Material.ANVIL), event -> openCategory(ctx, event, this::enhanceCategory)));
        setButton(SLOT_ETC_CATEGORY, GuiButton.of(categoryIcon("기타", "yeowool_mailbox:unopened_box", Material.CHEST), event -> openCategory(ctx, event, this::etcCategory)));
    }

    private interface CategoryFactory {
        MenuCategoryGui build(MenuContext ctx);
    }

    private void openCategory(MenuContext ctx, org.bukkit.event.inventory.InventoryClickEvent event, CategoryFactory factory) {
        if (event.getWhoClicked() instanceof Player player) {
            factory.build(ctx).open(player);
        }
    }

    /** {@code customIconId} (a real ItemsAdder item from another already-installed pack, verified via /iagive) if given, else {@code fallback}. */
    private ItemStack categoryIcon(String label, String customIconId, Material fallback) {
        ItemStack stack = customIconId == null ? null : MenuBackgroundImages.icon(customIconId);
        stack = stack == null ? new ItemStack(fallback) : stack.clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 카테고리 화면들 ──────────────────────────────────────────────

    private MenuCategoryGui economyCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("경제", NamedTextColor.GOLD), List.of(
                dispatchEntry("돈", "돈", "moafarm_items:eventcoin"),
                dispatchEntry("은행", "은행", "moafarm_items:money_sack"),
                dispatchEntry("내캐시", "내캐시", "moafarm_items:content1_coin")
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui lifeCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("생활", NamedTextColor.GOLD), List.of(
                dispatchEntry("직업", "직업", "medival_jobs:medival_jobs_miner"),
                dispatchEntry("가방", "가방", null, Material.BUNDLE),
                dispatchEntry("도감", "도감"),
                dispatchEntry("낚시대회", "낚시대회", "fishing_expansion:golden_fishing_rod")
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui marketCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("상점/시장", NamedTextColor.GOLD), List.of(
                dispatchEntry("상점", "상점이동", "playerwarps_gui:pwarp_shop"),
                dispatchEntry("거래", "거래", "moafarm_items:shop_normal"),
                dispatchEntry("경매", "경매", null, Material.BELL)
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui landCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("토지", NamedTextColor.GOLD), List.of(
                dispatchEntry("토지 관리", "토지", null, Material.FILLED_MAP),
                dispatchEntry("마을랭킹", "마을랭킹", "daily_quest:button_leaderboard")
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui teleportCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("텔레포트", NamedTextColor.GOLD), List.of(
                dispatchEntry("홈", "홈", "playerwarps_gui:pwarp_home"),
                dispatchEntry("워프", "워프", "playerwarps_gui:default_warpitem"),
                dispatchEntry("플레이어워프", "플레이어워프", "playerwarps_gui:all_warpsicon"),
                dispatchEntry("무작위 순간이동", "rtp", "yeowool_rtp:rtp_pin")
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui communityCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_27_v2", 3, ctx.config().categoryBackgroundOffsetPx(), Component.text("커뮤니티", NamedTextColor.GOLD), List.of(
                dispatchEntry("프로필", "프로필", null, Material.PLAYER_HEAD),
                dispatchEntry("내정보", "내정보", null, Material.WRITTEN_BOOK),
                dispatchEntry("칭호", "칭호", "daily_quest:rookie_badge"),
                dispatchEntry("친구", "친구", null, Material.NAME_TAG),
                dispatchEntry("커플", "커플", null, Material.POPPY)
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui questCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("퀘스트/이벤트", NamedTextColor.GOLD), List.of(
                dispatchEntry("일일 퀘스트", "일일퀘스트", "yeowool_battlepass:bp_questbook_gray"),
                dispatchEntry("주간 퀘스트", "주간퀘스트", "yeowool_battlepass:bp_questbook_gray"),
                dispatchEntry("출석체크", "출석체크", "daily_quest:reward"),
                dispatchEntry("이벤트", "이벤트", "yeowool_battlepass:bp_reward_unclaimed"),
                new MenuEntry(Component.text("배틀패스", NamedTextColor.GOLD), List.of(), "yeowool_battlepass:bp_questbook", player ->
                        new BattlePassPortalGui(ctx.core(), ctx.battlePassManager(), ctx.messages(), player).open(player))
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui enhanceCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_18_v2", 2, ctx.config().categoryBackgroundOffsetPx(), Component.text("강화", NamedTextColor.GOLD), List.of(
                dispatchEntry("강화", "강화", "yeowool_enhance:book_unique"),
                dispatchEntry("인챈트강화", "인챈트강화", "yeowool_enhance:book_elite")
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    private MenuCategoryGui etcCategory(MenuContext ctx) {
        return new MenuCategoryGui("menu_bg_27_v2", 3, ctx.config().categoryBackgroundOffsetPx(), Component.text("기타", NamedTextColor.GOLD), List.of(
                dispatchEntry("우편함", "우편함", "yeowool_mailbox:unopened_box"),
                dispatchEntry("랭킹", "랭킹", "daily_quest:button_leaderboard"),
                dispatchEntry("신고", "신고", null, Material.WRITABLE_BOOK),
                dispatchEntry("쿠폰", "쿠폰", null, Material.PAPER),
                dispatchEntry("길라잡이", "길라잡이", "moafarm_items:recipe_book"),
                dispatchEntry("도움말", "여울도움말", null, Material.BOOK)
        ), player -> new MenuHubGui(ctx, player).open(player));
    }

    /** Runs {@code command} as the player, exactly like typing it in chat - keeps every other module's existing command/GUI as the single source of truth instead of duplicating it here. */
    private MenuEntry dispatchEntry(String label, String command) {
        return dispatchEntry(label, command, null, null);
    }

    /** Same as {@link #dispatchEntry(String, String)}, with a specific ItemsAdder icon instead of the transparent fallback. */
    private MenuEntry dispatchEntry(String label, String command, String iconId) {
        return dispatchEntry(label, command, iconId, null);
    }

    /** Same as {@link #dispatchEntry(String, String)}, with an ItemsAdder icon (verified via /iagive) and/or a vanilla fallback when no dedicated icon was found. */
    private MenuEntry dispatchEntry(String label, String command, String iconId, Material fallbackMaterial) {
        return new MenuEntry(Component.text(label, NamedTextColor.GOLD), List.of(), iconId, fallbackMaterial,
                player -> player.performCommand(command));
    }
}
