package com.yeowool.community.battlepass;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
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

/** {@code /배틀패스} hub: current tier/points at a glance, a way into the reward track, and the premium purchase button. */
public final class BattlePassPortalGui extends YeowoolGui {

    // bp_portal_bg is authored for a 6-row (54-slot) window (matches the source pack's own
    // portal.yml, "menu-rows: 6") - a 3-row window left the art extending past the actual
    // inventory frame with the button icons floating below the clickable area entirely.
    // Each button is a block of same-icon slots (matches the source pack's own layout, which
    // repeats one item across a whole rectangular region rather than a single slot) using the
    // transparent bp_air icon so the hand-drawn background art shows through undisturbed.
    private static final int[] STATS_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21};
    private static final int[] QUESTS_SLOTS = {23, 24, 25, 26};
    private static final int[] PREMIUM_SLOTS = {45, 46, 47, 48};
    private static final int[] REWARDS_SLOTS = {50, 51, 52, 53};
    private static final int PROGRESS_BAR_SEGMENTS = 20;

    private final YeowoolCoreAPI core;
    private final BattlePassManager manager;
    private final MessageService messages;
    private boolean purchaseArmed;

    public BattlePassPortalGui(YeowoolCoreAPI core, BattlePassManager manager, MessageService messages, Player viewer) {
        super(54, title(manager, viewer));
        this.core = core;
        this.manager = manager;
        this.messages = messages;

        PlayerData data = core.playerData().getOnline(viewer.getUniqueId());
        long points = manager.points(data);
        boolean premium = manager.premiumUnlocked(data);
        int freeTier = manager.currentTier(BattlePassTrack.FREE, points);
        int premiumTier = manager.currentTier(BattlePassTrack.PREMIUM, points);

        GuiButton statsButton = GuiButton.display(statsIcon(points, freeTier, premiumTier, premium));
        for (int slot : STATS_SLOTS) {
            setButton(slot, statsButton);
        }
        GuiButton rewardsButton = GuiButton.of(rewardsIcon(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new BattlePassRewardsGui(core, manager, messages, player, 0).open(player);
            }
        });
        for (int slot : REWARDS_SLOTS) {
            setButton(slot, rewardsButton);
        }
        GuiButton questsButton = GuiButton.of(questsIcon(), event -> {
            if (event.getWhoClicked() instanceof Player player) {
                new BattlePassQuestOverviewGui(manager, player).open(player);
            }
        });
        for (int slot : QUESTS_SLOTS) {
            setButton(slot, questsButton);
        }
        refreshPremiumButton(premium);
    }

    private void refreshPremiumButton(boolean premium) {
        GuiButton premiumButton = GuiButton.of(premiumIcon(premium, purchaseArmed), event -> {
            if (premium || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!purchaseArmed) {
                purchaseArmed = true;
                refreshPremiumButton(false);
                return;
            }
            PlayerData data = core.playerData().getOnline(player.getUniqueId());
            BattlePassManager.PurchaseResult result = manager.purchasePremium(player, data);
            switch (result) {
                case SUCCESS -> {
                    messages.send(player, "battlepass.premium-purchase-success",
                            Placeholder.unparsed("amount", String.format("%,d", manager.config().premiumPriceCash())));
                    player.closeInventory();
                }
                case INSUFFICIENT_CASH -> messages.send(player, "battlepass.insufficient-cash");
                case ALREADY_UNLOCKED -> messages.send(player, "battlepass.already-premium");
            }
        });
        for (int slot : PREMIUM_SLOTS) {
            setButton(slot, premiumButton);
        }
    }

    private static Component title(BattlePassManager manager, Player viewer) {
        Component fallback = Component.text("배틀패스 - 시즌 " + manager.config().season(), NamedTextColor.GOLD);
        return BattlePassBackgroundImages.title(manager.config().backgroundOffsetPx(), "bp_portal_bg", fallback);
    }

    /** {@code bp_air} - a fully transparent 16x16 texture - so a button's slots let the hand-drawn background art show through instead of covering it with a vanilla icon. */
    private ItemStack transparentIcon(Material fallback) {
        ItemStack stack = BattlePassBackgroundImages.icon("yeowool_battlepass:bp_air");
        return stack == null ? new ItemStack(fallback) : stack.clone();
    }

    private ItemStack statsIcon(long points, int freeTier, int premiumTier, boolean premium) {
        ItemStack stack = transparentIcon(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();

        Component logo = BattlePassBackgroundImages.title(-8, "bp_logo", Component.empty());
        meta.displayName(logo.append(Component.text(" 시즌 " + manager.config().season() + " 배틀패스", NamedTextColor.GOLD, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>(List.of(
                Component.text("", NamedTextColor.GRAY),
                Component.text("포인트: " + String.format("%,d", points), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        Component progress = progressBarLine(points, freeTier);
        if (progress != null) {
            lore.add(progress);
        }
        lore.add(Component.text("무료 티어: " + freeTier, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("프리미엄 티어: " + premiumTier, premium ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("", NamedTextColor.GRAY));
        lore.add(Component.text("프리미엄 상태: " + (premium ? "보유 중" : "미보유"), premium ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** "다음 티어까지" bar (FREE track) - {@code null} if the next tier isn't configured yet, or ItemsAdder can't render it. */
    private Component progressBarLine(long points, int currentFreeTier) {
        BattlePassRewardStore.TierReward next = manager.rewardStore().get(BattlePassTrack.FREE, currentFreeTier + 1);
        if (next == BattlePassRewardStore.UNDEFINED) {
            return null;
        }
        long currentRequirement = currentFreeTier == 0 ? 0 : manager.rewardStore().get(BattlePassTrack.FREE, currentFreeTier).requiredPoints();
        long span = next.requiredPoints() - currentRequirement;
        double percent = span <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, (points - currentRequirement) / (double) span));
        Component bar = BattlePassBackgroundImages.progressBar((int) Math.round(percent * PROGRESS_BAR_SEGMENTS));
        if (bar == null) {
            return null;
        }
        return Component.text("다음 티어까지: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(bar);
    }

    private ItemStack questsIcon() {
        ItemStack stack = transparentIcon(Material.WRITTEN_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("퀘스트", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("클릭해서 일일/주간 퀘스트로 이동하세요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("퀘스트를 완료하면 배틀패스 포인트를 받습니다", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack rewardsIcon() {
        ItemStack stack = transparentIcon(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("보상 보기", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭해서 티어별 보상을 확인하세요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack premiumIcon(boolean premium, boolean armed) {
        ItemStack stack = transparentIcon(premium ? Material.NETHERITE_INGOT : armed ? Material.LIME_DYE : Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        if (premium) {
            meta.displayName(Component.text("프리미엄 보유 중", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("프리미엄 보상을 받을 수 있습니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        } else {
            meta.displayName(Component.text("프리미엄 구매", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("가격: " + String.format("%,d", manager.config().premiumPriceCash()) + "캐시", NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false),
                    armed
                            ? Component.text("한 번 더 클릭하면 구매됩니다", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                            : Component.text("클릭하여 구매", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
