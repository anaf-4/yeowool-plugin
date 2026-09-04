package com.yeowool.community.quest;

import com.yeowool.community.battlepass.BattlePassManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /일일퀘스트}/{@code /주간퀘스트}'s quest cycles — same mechanic, just a different
 * reset cadence and config pool, so both share this one class parameterized
 * by {@link Period} rather than duplicating the roll/progress/claim logic.
 * Progress is measured as a delta against an existing statistic (e.g.
 * {@code life.mining.mined}) captured as a per-quest baseline at roll time,
 * so any action that already feeds a job/title/dex statistic automatically
 * counts toward any quest built on that same key — no new listeners needed.
 * All state lives as {@link PlayerData} settings, same as every other
 * per-player flag in this plugin. Attendance is deliberately separate (see
 * {@code AttendanceManager}) since {@code /출석체크} is its own command now.
 */
public final class QuestManager {

    /** {@code yw_player_statistics} key for the lifetime (daily+weekly combined) claimed-quest counter backing the badge/leaderboard system. */
    public static final String TOTAL_COMPLETED_STAT_KEY = "community.quest.total-completed";

    public enum Period {
        DAILY("daily"), WEEKLY("weekly");

        private final String configKey;

        Period(String configKey) {
            this.configKey = configKey;
        }

        String configKey() {
            return configKey;
        }
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final Map<Period, List<QuestDefinition>> pools = new EnumMap<>(Period.class);
    private final Map<Period, Map<QuestDifficulty, Integer>> counts = new EnumMap<>(Period.class);
    private BattlePassManager battlePassManager;

    public QuestManager(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        reload();
    }

    public void reload() {
        for (Period period : Period.values()) {
            List<QuestDefinition> parsed = new ArrayList<>();
            for (Map<?, ?> entry : plugin.getConfig().getMapList(period.configKey() + ".quest-pool")) {
                try {
                    parsed.add(new QuestDefinition(
                            entry.get("id").toString(),
                            entry.get("stat-key").toString(),
                            ((Number) entry.get("target")).longValue(),
                            entry.get("display").toString(),
                            entry.containsKey("reward-on") ? ((Number) entry.get("reward-on")).longValue() : 0L,
                            QuestDifficulty.parse(entry.get("difficulty") == null ? null : entry.get("difficulty").toString(), QuestDifficulty.MEDIUM)));
                } catch (Exception e) {
                    plugin.getLogger().warning(period.configKey() + ".quest-pool 설정 항목이 잘못되었습니다: " + entry);
                }
            }
            pools.put(period, List.copyOf(parsed));

            Map<QuestDifficulty, Integer> periodCounts = new EnumMap<>(QuestDifficulty.class);
            periodCounts.put(QuestDifficulty.EASY, plugin.getConfig().getInt(period.configKey() + ".easy-count", 4));
            periodCounts.put(QuestDifficulty.MEDIUM, plugin.getConfig().getInt(period.configKey() + ".medium-count", 4));
            periodCounts.put(QuestDifficulty.HARD, plugin.getConfig().getInt(period.configKey() + ".hard-count", 4));
            counts.put(period, periodCounts);
        }
    }

    public List<QuestDefinition> pool(Period period) {
        return pools.get(period);
    }

    /** How many quests of {@code difficulty} are rolled per {@code period} — see {@code <period>.<difficulty>-count} in config.yml. */
    public int countFor(Period period, QuestDifficulty difficulty) {
        return counts.get(period).getOrDefault(difficulty, 0);
    }

    /** Vertical position tuning for the {@code daily_quest:*_bg} GUI backgrounds — see {@link QuestBackgroundImages}. */
    public int backgroundOffsetPx() {
        return plugin.getConfig().getInt("quest.gui-background-offset", -8);
    }

    /** Lifetime claimed-quest count backing the badge/leaderboard system. */
    public long totalCompleted(PlayerData data) {
        return data.getStatistic(TOTAL_COMPLETED_STAT_KEY);
    }

    /** {@code 2026-08-27} for {@link Period#DAILY}, ISO week like {@code 2026-W35} for {@link Period#WEEKLY}. */
    private String periodId(Period period) {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        if (period == Period.DAILY) {
            return now.toString();
        }
        return now.get(WeekFields.ISO.weekBasedYear()) + "-W" + String.format("%02d", now.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    private String settingKey(Period period, String suffix) {
        return "quest." + period.configKey() + "." + suffix;
    }

    /**
     * Re-rolls this player's quests for {@code period} if they haven't been
     * rolled yet this period, OR if the previously-rolled set no longer
     * matches the current pool/counts — either an id that no longer resolves
     * (admin edited {@code quest-pool}), or a difficulty tier that has fewer
     * rolled quests than {@code <period>.<difficulty>-count} now calls for
     * (admin raised the count after this player already rolled) — otherwise
     * they'd be stuck with a partly-empty board until the next natural reset.
     * Safe to call every time the quest GUI opens.
     */
    public void ensureRolled(Player player, Period period) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        String currentPeriodId = periodId(period);
        List<QuestDefinition> pool = pools.get(period);
        if (pool.isEmpty()) {
            return;
        }
        boolean samePeriod = currentPeriodId.equals(data.getSetting(settingKey(period, "period"), ""));
        List<QuestDefinition> rolled = samePeriod
                ? splitCsv(data.getSetting(settingKey(period, "ids"), "")).stream()
                        .map(id -> find(period, id)).filter(Optional::isPresent).map(Optional::get).toList()
                : List.of();
        boolean staleIds = samePeriod && rolled.size() != splitCsv(data.getSetting(settingKey(period, "ids"), "")).size();
        boolean countsMismatch = samePeriod && java.util.Arrays.stream(QuestDifficulty.values())
                .anyMatch(difficulty -> rolled.stream().filter(q -> q.difficulty() == difficulty).count()
                        < Math.min(countFor(period, difficulty), pool.stream().filter(q -> q.difficulty() == difficulty).count()));
        if (samePeriod && !staleIds && !countsMismatch) {
            return;
        }

        List<QuestDefinition> chosen = new ArrayList<>();
        for (QuestDifficulty difficulty : QuestDifficulty.values()) {
            List<QuestDefinition> tierPool = pool.stream().filter(q -> q.difficulty() == difficulty).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Collections.shuffle(tierPool, ThreadLocalRandom.current());
            int count = countFor(period, difficulty);
            chosen.addAll(tierPool.subList(0, Math.min(count, tierPool.size())));
        }

        List<String> ids = new ArrayList<>();
        for (QuestDefinition quest : chosen) {
            ids.add(quest.id());
            data.setSetting(settingKey(period, "baseline." + quest.id()), String.valueOf(data.getStatistic(quest.statKey())));
        }
        data.setSetting(settingKey(period, "ids"), String.join(",", ids));
        data.setSetting(settingKey(period, "claimed"), "");
        data.setSetting(settingKey(period, "period"), currentPeriodId);
    }

    public record QuestProgress(QuestDefinition quest, long progress, boolean claimed) {
        public boolean isComplete() {
            return progress >= quest.target();
        }
    }

    /** Wired after both managers exist ({@code YeowoolCommunity}) so quest completions also earn battle pass points. */
    public void setBattlePassManager(BattlePassManager battlePassManager) {
        this.battlePassManager = battlePassManager;
    }

    public List<QuestProgress> activeQuests(PlayerData data, Period period) {
        List<QuestProgress> result = new ArrayList<>();
        Set<String> claimed = splitCsv(data.getSetting(settingKey(period, "claimed"), ""));
        for (String id : splitCsv(data.getSetting(settingKey(period, "ids"), ""))) {
            find(period, id).ifPresent(quest -> {
                long baseline = parseLongOr(data.getSetting(settingKey(period, "baseline." + id), "0"), 0);
                long progress = Math.min(quest.target(), Math.max(0, data.getStatistic(quest.statKey()) - baseline));
                result.add(new QuestProgress(quest, progress, claimed.contains(id)));
            });
        }
        return result;
    }

    public enum ClaimResult { SUCCESS, NOT_ACTIVE_QUEST, NOT_COMPLETE, ALREADY_CLAIMED }

    public ClaimResult claimQuest(Player player, Period period, String questId) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        Optional<QuestDefinition> questOpt = find(period, questId);
        Set<String> ids = splitCsv(data.getSetting(settingKey(period, "ids"), ""));
        if (questOpt.isEmpty() || !ids.contains(questId)) {
            return ClaimResult.NOT_ACTIVE_QUEST;
        }
        Set<String> claimed = new LinkedHashSet<>(splitCsv(data.getSetting(settingKey(period, "claimed"), "")));
        if (claimed.contains(questId)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        QuestDefinition quest = questOpt.get();
        long baseline = parseLongOr(data.getSetting(settingKey(period, "baseline." + questId), "0"), 0);
        if (data.getStatistic(quest.statKey()) - baseline < quest.target()) {
            return ClaimResult.NOT_COMPLETE;
        }

        claimed.add(questId);
        data.setSetting(settingKey(period, "claimed"), String.join(",", claimed));
        if (quest.rewardOn() > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), quest.rewardOn(), "YeowoolCommunity",
                    (period == Period.DAILY ? "일일" : "주간") + " 퀘스트: " + quest.id());
        }
        data.addStatistic(TOTAL_COMPLETED_STAT_KEY, 1);
        if (battlePassManager != null) {
            battlePassManager.addPointsForQuest(data, quest.difficulty());
        }
        return ClaimResult.SUCCESS;
    }

    private Optional<QuestDefinition> find(Period period, String id) {
        return pools.get(period).stream().filter(q -> q.id().equals(id)).findFirst();
    }

    private Set<String> splitCsv(String raw) {
        return raw.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(raw.split(",")));
    }

    private long parseLongOr(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
