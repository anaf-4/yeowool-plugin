package com.yeowool.community.quest;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * {@code /출석체크}: a daily reward with a consecutive-day streak (and
 * milestone bonuses), plus independent weekly/monthly cooldown-based
 * rewards alongside it — the three-tier layout ({@link AttendanceGui})
 * matches the "Rewards UI by MCMobs" ItemsAdder asset pack the daily/weekly/
 * monthly icons and background come from. Weekly/monthly don't have a
 * streak concept (a plain "N hours since last claim" cooldown), unlike
 * daily which resets on the calendar day and breaks the streak if a day is
 * skipped. Separate from {@link QuestManager}'s quest cycles.
 */
public final class AttendanceManager {

    private static final String DATE_SETTING = "attendance.date";
    private static final String STREAK_SETTING = "attendance.streak";
    private static final String WEEKLY_LAST_CLAIM_SETTING = "attendance.weekly.last-claim";
    private static final String MONTHLY_LAST_CLAIM_SETTING = "attendance.monthly.last-claim";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final AttendanceRewardStore rewardStore;
    private Map<Integer, Long> streakBonuses = Map.of();
    private long weeklyCooldownMillis;
    private long monthlyCooldownMillis;
    private String guiBackground = "rewards_blue";

    public AttendanceManager(JavaPlugin plugin, YeowoolCoreAPI core, AttendanceRewardStore rewardStore) {
        this.plugin = plugin;
        this.core = core;
        this.rewardStore = rewardStore;
        reload();
    }

    public void reload() {
        Map<Integer, Long> bonuses = new TreeMap<>();
        var section = plugin.getConfig().getConfigurationSection("attendance.streak-bonus");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    bonuses.put(Integer.parseInt(key), section.getLong(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("attendance.streak-bonus 키가 숫자가 아닙니다: " + key);
                }
            }
        }
        this.streakBonuses = Map.copyOf(bonuses);

        this.weeklyCooldownMillis = plugin.getConfig().getLong("attendance.weekly.cooldown-hours", 168) * 3_600_000L;
        this.monthlyCooldownMillis = plugin.getConfig().getLong("attendance.monthly.cooldown-hours", 720) * 3_600_000L;
        this.guiBackground = plugin.getConfig().getString("attendance.gui.background", "rewards_blue");
    }

    public String guiBackground() {
        return guiBackground;
    }

    private String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    public record Status(boolean claimedToday, int streak) {
    }

    public Status status(PlayerData data) {
        boolean claimedToday = today().equals(data.getSetting(DATE_SETTING, ""));
        return new Status(claimedToday, (int) parseLongOr(data.getSetting(STREAK_SETTING, "0"), 0));
    }

    public record Claim(int streak, long reward, long streakBonus, CurrencyType currency) {
    }

    /** Empty if already claimed today. Streak continues only if yesterday (not further back) was the last claim. */
    public Optional<Claim> claim(Player player) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        String today = today();
        String lastDate = data.getSetting(DATE_SETTING, "");
        if (today.equals(lastDate)) {
            return Optional.empty();
        }

        int previousStreak = (int) parseLongOr(data.getSetting(STREAK_SETTING, "0"), 0);
        String yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString();
        int newStreak = yesterday.equals(lastDate) ? previousStreak + 1 : 1;

        data.setSetting(DATE_SETTING, today);
        data.setSetting(STREAK_SETTING, String.valueOf(newStreak));

        AttendanceRewardStore.TierReward tierReward = rewardStore.get(AttendanceRewardStore.Tier.DAILY);
        if (tierReward.amount() > 0) {
            modifyCurrency(player.getUniqueId(), tierReward.currency(), tierReward.amount(), "출석 보상");
        }
        giveItems(player, tierReward.items());
        long bonus = streakBonuses.getOrDefault(newStreak, 0L);
        if (bonus > 0) {
            modifyCurrency(player.getUniqueId(), tierReward.currency(), bonus, "출석 " + newStreak + "일 연속 보너스");
        }
        return Optional.of(new Claim(newStreak, tierReward.amount(), bonus, tierReward.currency()));
    }

    public record CooldownStatus(boolean available, long remainingMillis) {
    }

    public CooldownStatus weeklyStatus(PlayerData data) {
        return cooldownStatus(data, WEEKLY_LAST_CLAIM_SETTING, weeklyCooldownMillis);
    }

    public CooldownStatus monthlyStatus(PlayerData data) {
        return cooldownStatus(data, MONTHLY_LAST_CLAIM_SETTING, monthlyCooldownMillis);
    }

    private CooldownStatus cooldownStatus(PlayerData data, String settingKey, long cooldownMillis) {
        long lastClaim = parseLongOr(data.getSetting(settingKey, "0"), 0);
        long elapsed = System.currentTimeMillis() - lastClaim;
        return elapsed >= cooldownMillis
                ? new CooldownStatus(true, 0)
                : new CooldownStatus(false, cooldownMillis - elapsed);
    }

    public record TierClaim(long reward, CurrencyType currency) {
    }

    /** Empty (with no side effect) if still on cooldown. */
    public Optional<TierClaim> claimWeekly(Player player) {
        return claimCooldownTier(player, WEEKLY_LAST_CLAIM_SETTING, weeklyCooldownMillis, AttendanceRewardStore.Tier.WEEKLY, "주간 출석 보상");
    }

    /** Empty (with no side effect) if still on cooldown. */
    public Optional<TierClaim> claimMonthly(Player player) {
        return claimCooldownTier(player, MONTHLY_LAST_CLAIM_SETTING, monthlyCooldownMillis, AttendanceRewardStore.Tier.MONTHLY, "월간 출석 보상");
    }

    private Optional<TierClaim> claimCooldownTier(Player player, String settingKey, long cooldownMillis, AttendanceRewardStore.Tier tier, String reason) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        if (!cooldownStatus(data, settingKey, cooldownMillis).available()) {
            return Optional.empty();
        }
        data.setSetting(settingKey, String.valueOf(System.currentTimeMillis()));
        AttendanceRewardStore.TierReward tierReward = rewardStore.get(tier);
        if (tierReward.amount() > 0) {
            modifyCurrency(player.getUniqueId(), tierReward.currency(), tierReward.amount(), reason);
        }
        giveItems(player, tierReward.items());
        return Optional.of(new TierClaim(tierReward.amount(), tierReward.currency()));
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

    private long parseLongOr(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
