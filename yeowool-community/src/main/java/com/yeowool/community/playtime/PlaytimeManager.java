package com.yeowool.community.playtime;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /플레이타임}: four milestone rewards (1/6/12/24시간), measured against
 * <b>오늘 하루</b> total playtime — not lifetime (that's {@code
 * PlaytimeTracker#STAT_KEY}, a separate never-reset statistic used by
 * {@code /내정보} etc.) — so both the minute count and each tier's claimed
 * flag reset at local midnight and can be earned again the next day. The
 * per-minute increment ({@link #tickDaily}) is called from {@code
 * PlaytimeTracker}'s existing once-a-minute loop rather than a second
 * scheduler, but keeps its own settings keys so it never touches the
 * lifetime statistic.
 */
public final class PlaytimeManager {

    private static final String CLAIMED_SETTING_PREFIX = "playtime.claimed.";
    private static final String DAILY_MINUTES_SETTING = "playtime.daily-minutes";
    private static final String DAILY_DATE_SETTING = "playtime.daily-date";

    private final YeowoolCoreAPI core;
    private final PlaytimeRewardStore rewardStore;

    public PlaytimeManager(YeowoolCoreAPI core, PlaytimeRewardStore rewardStore) {
        this.core = core;
        this.rewardStore = rewardStore;
    }

    private static String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    /** Called once per online minute (see {@code PlaytimeTracker}) — resets the daily counter on a new day, then adds a minute. */
    public static void tickDaily(PlayerData data) {
        String today = today();
        if (!today.equals(data.getSetting(DAILY_DATE_SETTING, ""))) {
            data.setSetting(DAILY_DATE_SETTING, today);
            data.setSetting(DAILY_MINUTES_SETTING, "0");
        }
        long minutes = parseLongOr(data.getSetting(DAILY_MINUTES_SETTING, "0"), 0) + 1;
        data.setSetting(DAILY_MINUTES_SETTING, String.valueOf(minutes));
    }

    /** 0 if today's date doesn't match the stored counter's date yet (server just restarted, or the player hasn't been online today at all). */
    private static long dailyMinutes(PlayerData data) {
        if (!today().equals(data.getSetting(DAILY_DATE_SETTING, ""))) {
            return 0;
        }
        return parseLongOr(data.getSetting(DAILY_MINUTES_SETTING, "0"), 0);
    }

    private static long parseLongOr(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record Status(boolean unlocked, boolean claimed, long currentMinutes, long requiredMinutes) {
    }

    public Status status(PlayerData data, PlaytimeRewardStore.Tier tier) {
        long minutes = dailyMinutes(data);
        boolean claimed = isClaimedToday(data, tier);
        return new Status(minutes >= tier.requiredMinutes(), claimed, minutes, tier.requiredMinutes());
    }

    private boolean isClaimedToday(PlayerData data, PlaytimeRewardStore.Tier tier) {
        return today().equals(data.getSetting(CLAIMED_SETTING_PREFIX + tier.key(), ""));
    }

    public record Claim(long reward, CurrencyType currency) {
    }

    public enum ClaimFailure { NOT_UNLOCKED, ALREADY_CLAIMED }

    /** Left empty (with the reason) if not unlocked yet or already claimed today. */
    public Optional<Claim> claim(Player player, PlaytimeRewardStore.Tier tier) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        Status status = status(data, tier);
        if (!status.unlocked() || status.claimed()) {
            return Optional.empty();
        }

        data.setSetting(CLAIMED_SETTING_PREFIX + tier.key(), today());
        PlaytimeRewardStore.TierReward reward = rewardStore.get(tier);
        if (reward.amount() > 0) {
            modifyCurrency(player.getUniqueId(), reward.currency(), reward.amount(), tier.key() + " 플레이타임 보상");
        }
        giveItems(player, reward.items());
        return Optional.of(new Claim(reward.amount(), reward.currency()));
    }

    private void modifyCurrency(UUID uuid, CurrencyType currency, long amount, String reason) {
        if (currency == CurrencyType.CASH) {
            core.economyData().modifyCashBalance(uuid, amount, "YeowoolCommunity", reason);
        } else {
            core.economyData().modifyBalance(uuid, amount, "YeowoolCommunity", reason);
        }
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            var leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }
}
