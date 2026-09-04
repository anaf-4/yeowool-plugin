package com.yeowool.community.battlepass;

import com.yeowool.community.quest.QuestContext;
import com.yeowool.community.quest.QuestDifficulty;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Core battle-pass logic: points/premium/claimed-tiers all live as ordinary {@link PlayerData}
 * statistics/settings (same pattern {@code QuestManager} and the autofarm vouchers already use)
 * rather than a bespoke table+cache, keyed per season so bumping {@code battlepass.season} in
 * config.yml naturally starts everyone fresh without needing any reset logic.
 */
public final class BattlePassManager {

    private final YeowoolCoreAPI core;
    private final BattlePassConfig config;
    private final BattlePassRewardStore rewardStore;
    private final QuestContext questContext;

    public BattlePassManager(YeowoolCoreAPI core, BattlePassConfig config, BattlePassRewardStore rewardStore, QuestContext questContext) {
        this.core = core;
        this.config = config;
        this.rewardStore = rewardStore;
        this.questContext = questContext;
    }

    public BattlePassConfig config() {
        return config;
    }

    public BattlePassRewardStore rewardStore() {
        return rewardStore;
    }

    /** Lets the quest-overview screens open the real {@code QuestBoardGui} without threading 7 params through separately. */
    public QuestContext questContext() {
        return questContext;
    }

    private String pointsKey() {
        return "battlepass.s" + config.season() + ".points";
    }

    private String premiumKey() {
        return "battlepass.s" + config.season() + ".premium";
    }

    private String claimedKey(BattlePassTrack track) {
        return "battlepass.s" + config.season() + ".claimed." + track.key();
    }

    public long points(PlayerData data) {
        return data.getStatistic(pointsKey());
    }

    public boolean premiumUnlocked(PlayerData data) {
        return "true".equals(data.getSetting(premiumKey(), ""));
    }

    public Set<Integer> claimedTiers(PlayerData data, BattlePassTrack track) {
        return splitCsv(data.getSetting(claimedKey(track), ""));
    }

    public void addPoints(PlayerData data, long amount) {
        if (amount > 0) {
            data.addStatistic(pointsKey(), amount);
        }
    }

    public void addPointsForQuest(PlayerData data, QuestDifficulty difficulty) {
        addPoints(data, config.pointsFor(difficulty));
    }

    /** Highest tier this many points has reached on the given track (0 if none). */
    public int currentTier(BattlePassTrack track, long points) {
        int tier = 0;
        for (int candidate : rewardStore.configuredTiers(track)) {
            if (points >= rewardStore.get(track, candidate).requiredPoints()) {
                tier = Math.max(tier, candidate);
            }
        }
        return tier;
    }

    public enum PurchaseResult { SUCCESS, ALREADY_UNLOCKED, INSUFFICIENT_CASH }

    public PurchaseResult purchasePremium(Player player, PlayerData data) {
        if (premiumUnlocked(data)) {
            return PurchaseResult.ALREADY_UNLOCKED;
        }
        long price = config.premiumPriceCash();
        if (!core.economyData().hasCashBalance(player.getUniqueId(), price)) {
            return PurchaseResult.INSUFFICIENT_CASH;
        }
        core.economyData().modifyCashBalance(player.getUniqueId(), -price, "YeowoolCommunity",
                "배틀패스 프리미엄 구매 (시즌 " + config.season() + ")");
        data.setSetting(premiumKey(), "true");
        return PurchaseResult.SUCCESS;
    }

    /** Admin override - grants premium without charging cash (e.g. support compensation). */
    public void grantPremium(PlayerData data) {
        data.setSetting(premiumKey(), "true");
    }

    public enum ClaimResult { SUCCESS, PREMIUM_REQUIRED, NOT_UNLOCKED, ALREADY_CLAIMED, NO_REWARD }

    public ClaimResult claim(Player player, PlayerData data, BattlePassTrack track, int tier) {
        BattlePassRewardStore.TierReward reward = rewardStore.get(track, tier);
        if (reward == BattlePassRewardStore.UNDEFINED) {
            return ClaimResult.NO_REWARD;
        }
        if (track == BattlePassTrack.PREMIUM && !premiumUnlocked(data)) {
            return ClaimResult.PREMIUM_REQUIRED;
        }
        if (points(data) < reward.requiredPoints()) {
            return ClaimResult.NOT_UNLOCKED;
        }
        Set<Integer> claimed = claimedTiers(data, track);
        if (claimed.contains(tier)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        claimed.add(tier);
        data.setSetting(claimedKey(track), joinCsv(claimed));

        String reason = "배틀패스 보상 (" + track.label() + " Tier " + tier + ")";
        if (reward.amount() > 0) {
            if (reward.currency() == CurrencyType.CASH) {
                core.economyData().modifyCashBalance(player.getUniqueId(), reward.amount(), "YeowoolCommunity", reason);
            } else {
                core.economyData().modifyBalance(player.getUniqueId(), reward.amount(), "YeowoolCommunity", reason);
            }
        }
        for (ItemStack item : reward.items()) {
            core.mailbox().deliverOrStore(player.getUniqueId(), item, "YeowoolCommunity", reason);
        }
        return ClaimResult.SUCCESS;
    }

    private static Set<Integer> splitCsv(String raw) {
        Set<Integer> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // corrupt/stale entry - skip
            }
        }
        return result;
    }

    private static String joinCsv(Set<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (int value : values) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
