package com.yeowool.community.battlepass;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/**
 * {@code /배틀패스} → 보상 보기: the two-track view, 9 tiers per page (mirrors the reference
 * pack's layout exactly — free row 0-8, tier-number row 18-26, premium row 36-44, prev/next 45-53).
 */
public final class BattlePassRewardsGui extends YeowoolGui {

    private static final int[] FREE_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] TIER_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] PREMIUM_SLOTS = {36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] PREV_SLOTS = {45, 46, 47};
    private static final int[] NEXT_SLOTS = {51, 52, 53};
    private static final int TIERS_PER_PAGE = 9;

    public BattlePassRewardsGui(YeowoolCoreAPI core, BattlePassManager manager, MessageService messages, Player viewer, int page) {
        super(54, title(manager, viewer));

        PlayerData data = core.playerData().getOnline(viewer.getUniqueId());
        long points = manager.points(data);
        boolean premium = manager.premiumUnlocked(data);
        Set<Integer> claimedFree = manager.claimedTiers(data, BattlePassTrack.FREE);
        Set<Integer> claimedPremium = manager.claimedTiers(data, BattlePassTrack.PREMIUM);

        for (int i = 0; i < TIERS_PER_PAGE; i++) {
            int tier = page * TIERS_PER_PAGE + i + 1;

            setButton(TIER_SLOTS[i], GuiButton.display(tierIcon(tier, points, manager)));
            setButton(FREE_SLOTS[i], rewardButton(core, manager, messages, viewer, BattlePassTrack.FREE, tier, points, true, claimedFree));
            setButton(PREMIUM_SLOTS[i], rewardButton(core, manager, messages, viewer, BattlePassTrack.PREMIUM, tier, points, premium, claimedPremium));
        }

        GuiButton prev = GuiButton.of(navIcon("뒤로가기"), event -> {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (page > 0) {
                new BattlePassRewardsGui(core, manager, messages, player, page - 1).open(player);
            } else {
                new BattlePassPortalGui(core, manager, messages, player).open(player);
            }
        });
        for (int slot : PREV_SLOTS) {
            setButton(slot, prev);
        }
        int maxConfiguredTier = Math.max(manager.rewardStore().maxConfiguredTier(BattlePassTrack.FREE),
                manager.rewardStore().maxConfiguredTier(BattlePassTrack.PREMIUM));
        if ((page + 1) * TIERS_PER_PAGE + 1 <= maxConfiguredTier) {
            GuiButton next = GuiButton.of(navIcon("다음으로"), event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new BattlePassRewardsGui(core, manager, messages, player, page + 1).open(player);
                }
            });
            for (int slot : NEXT_SLOTS) {
                setButton(slot, next);
            }
        }
    }

    private GuiButton rewardButton(YeowoolCoreAPI core, BattlePassManager manager, MessageService messages, Player viewer,
                                    BattlePassTrack track, int tier, long points, boolean eligibleTrack, Set<Integer> claimed) {
        BattlePassRewardStore.TierReward reward = manager.rewardStore().get(track, tier);
        if (reward == BattlePassRewardStore.UNDEFINED) {
            return GuiButton.display(emptyIcon());
        }

        boolean unlocked = points >= reward.requiredPoints();
        boolean isClaimed = claimed.contains(tier);

        ItemStack icon;
        if (isClaimed) {
            icon = stateIcon("yeowool_battlepass:bp_reward_claimed", Material.LIME_STAINED_GLASS_PANE, track, tier, reward, "수령 완료", NamedTextColor.GREEN);
        } else if (!eligibleTrack) {
            icon = stateIcon("yeowool_battlepass:bp_reward_locked_gray", Material.GRAY_STAINED_GLASS_PANE, track, tier, reward, "프리미엄 구매 필요", NamedTextColor.RED);
        } else if (!unlocked) {
            icon = stateIcon("yeowool_battlepass:bp_reward_locked_green", Material.YELLOW_STAINED_GLASS_PANE, track, tier, reward,
                    "포인트 " + String.format("%,d", reward.requiredPoints()) + " 필요", NamedTextColor.YELLOW);
        } else {
            icon = stateIcon("yeowool_battlepass:bp_reward_unclaimed", Material.GOLD_INGOT, track, tier, reward, "클릭하여 수령", NamedTextColor.GREEN);
        }

        boolean canClaim = eligibleTrack && unlocked && !isClaimed;
        return GuiButton.of(icon, event -> {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!canClaim) {
                return;
            }
            PlayerData data = core.playerData().getOnline(player.getUniqueId());
            BattlePassManager.ClaimResult result = manager.claim(player, data, track, tier);
            switch (result) {
                case SUCCESS -> {
                    messages.send(player, "battlepass.claim-success");
                    new BattlePassRewardsGui(core, manager, messages, player, (tier - 1) / TIERS_PER_PAGE).open(player);
                }
                case PREMIUM_REQUIRED -> messages.send(player, "battlepass.premium-required");
                // Stale-icon clicks (an admin changed the tier, or the points requirement, between
                // this GUI rendering and the click landing) - previously these silently did
                // nothing, which looked like a dead button with no explanation.
                case NOT_UNLOCKED -> messages.send(player, "battlepass.not-unlocked");
                case ALREADY_CLAIMED -> messages.send(player, "battlepass.already-claimed");
                case NO_REWARD -> messages.send(player, "battlepass.no-reward");
            }
        });
    }

    private ItemStack stateIcon(String customIconId, Material fallback, BattlePassTrack track, int tier,
                                 BattlePassRewardStore.TierReward reward, String statusText, NamedTextColor statusColor) {
        ItemStack stack = BattlePassBackgroundImages.icon(customIconId);
        if (stack == null) {
            stack = new ItemStack(fallback);
        } else {
            stack = stack.clone();
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(track.label() + " Tier " + tier, NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("", NamedTextColor.GRAY),
                Component.text("필요 포인트: " + String.format("%,d", reward.requiredPoints()), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                rewardSummary(reward),
                Component.text("", NamedTextColor.GRAY),
                Component.text(statusText, statusColor).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private Component rewardSummary(BattlePassRewardStore.TierReward reward) {
        StringBuilder text = new StringBuilder("보상: ");
        boolean any = false;
        if (reward.amount() > 0) {
            text.append(String.format("%,d", reward.amount())).append(reward.currency().displayName());
            any = true;
        }
        if (!reward.items().isEmpty()) {
            if (any) {
                text.append(", ");
            }
            text.append("아이템 ").append(reward.items().size()).append("개");
            any = true;
        }
        if (!any) {
            text.append("없음");
        }
        return Component.text(text.toString(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack tierIcon(int tier, long points, BattlePassManager manager) {
        boolean anyUnlocked = points >= 0 && (manager.rewardStore().get(BattlePassTrack.FREE, tier).requiredPoints() <= points
                || manager.rewardStore().get(BattlePassTrack.PREMIUM, tier).requiredPoints() <= points);
        ItemStack stack = BattlePassBackgroundImages.icon(anyUnlocked ? "yeowool_battlepass:bp_unlocked" : "yeowool_battlepass:bp_locked");
        if (stack == null) {
            stack = new ItemStack(anyUnlocked ? Material.LIME_DYE : Material.GRAY_DYE);
        } else {
            stack = stack.clone();
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Tier " + tier, anyUnlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack emptyIcon() {
        ItemStack stack = BattlePassBackgroundImages.icon("yeowool_battlepass:bp_air");
        return stack != null ? stack.clone() : new ItemStack(Material.AIR);
    }

    /** {@code bp_air} - transparent - so the background art shows through instead of a vanilla arrow icon. */
    private static ItemStack navIcon(String label) {
        ItemStack stack = BattlePassBackgroundImages.icon("yeowool_battlepass:bp_air");
        stack = stack == null ? new ItemStack(Material.ARROW) : stack.clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component title(BattlePassManager manager, Player viewer) {
        Component fallback = Component.text("배틀패스 보상", NamedTextColor.GOLD);
        return BattlePassBackgroundImages.title(manager.config().rewardsBackgroundOffsetPx(), "bp_rewards_bg", fallback);
    }
}
