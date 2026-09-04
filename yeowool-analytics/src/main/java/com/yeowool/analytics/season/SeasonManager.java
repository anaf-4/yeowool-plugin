package com.yeowool.analytics.season;

import com.yeowool.analytics.MetricsCollector;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.PlayerDataResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Ends a season on a config-driven period ({@code season.period-days}):
 * rewards the top {@code season.score} standings (see
 * {@link SeasonScoreListener}), broadcasts the results, and resets the
 * statistic to 0 for everyone — offline top-N winners still get paid via
 * {@link PlayerDataResolver}. The last-reset timestamp lives in a small
 * local {@value #STATE_FILE} rather than a DB table, since it's this one
 * plugin instance's own scheduling state, not shared game data.
 */
public final class SeasonManager {

    private static final String STATE_FILE = "season-state.yml";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final MetricsCollector collector;
    private final SeasonRepository repository;
    private final ExecutorService executor;
    private final int periodDays;
    private final Map<Integer, Long> rewardTiers;
    private final boolean broadcast;

    public SeasonManager(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, MetricsCollector collector, SeasonRepository repository,
                          ExecutorService executor, int periodDays, Map<Integer, Long> rewardTiers, boolean broadcast) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.collector = collector;
        this.repository = repository;
        this.executor = executor;
        this.periodDays = periodDays;
        this.rewardTiers = rewardTiers;
        this.broadcast = broadcast;
    }

    /** Called periodically from {@code YeowoolAnalytics} — a no-op unless the configured period has actually elapsed. */
    public void checkAndRunIfDue() {
        long periodMillis = periodDays * 24L * 60L * 60L * 1000L;
        if (System.currentTimeMillis() - readLastReset() < periodMillis) {
            return;
        }
        runNow();
    }

    /** Forces a season end immediately — also used by the admin manual-trigger command. */
    public void runNow() {
        executor.execute(() -> {
            try {
                List<MetricsCollector.RankingRow> top = collector.topSeasonScore(rewardTiers.size());
                Bukkit.getScheduler().runTask(plugin, () -> finishSeason(top));
            } catch (Exception e) {
                plugin.getLogger().severe("시즌 랭킹 집계 실패: " + e.getMessage());
            }
        });
    }

    private void finishSeason(List<MetricsCollector.RankingRow> top) {
        int rank = 1;
        for (var row : top) {
            long reward = rewardTiers.getOrDefault(rank, 0L);
            int finalRank = rank;
            if (reward > 0) {
                PlayerDataResolver.resolve(plugin, core, row.username(), (data, online) -> {
                    core.economyData().modifyBalance(data.getUuid(), reward, "YeowoolAnalytics", "시즌 랭킹 " + finalRank + "위 보상");
                    if (online != null) {
                        messages.send(online, "season.reward-received",
                                Placeholder.unparsed("rank", String.valueOf(finalRank)),
                                Placeholder.unparsed("reward", String.format("%,d", reward)));
                    }
                }, () -> plugin.getLogger().warning("시즌 보상 대상을 찾을 수 없습니다: " + row.username()));
            }
            rank++;
        }

        if (broadcast) {
            broadcastResults(top);
        }

        executor.execute(() -> {
            try {
                repository.resetAllSeasonScores();
            } catch (Exception e) {
                plugin.getLogger().severe("시즌 점수 초기화 실패: " + e.getMessage());
            }
        });
        for (var player : Bukkit.getOnlinePlayers()) {
            core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(data -> {
                long current = data.getStatistic("season.score");
                if (current != 0) {
                    data.addStatistic("season.score", -current);
                }
            });
        }
        writeLastReset(System.currentTimeMillis());
    }

    private void broadcastResults(List<MetricsCollector.RankingRow> top) {
        if (top.isEmpty()) {
            messages.broadcast("season.broadcast-empty");
            return;
        }
        messages.broadcast("season.broadcast-header");
        int rank = 1;
        for (var row : top) {
            messages.broadcast("season.broadcast-entry",
                    Placeholder.unparsed("rank", String.valueOf(rank)),
                    Placeholder.unparsed("name", row.username()),
                    Placeholder.unparsed("value", String.format("%,d", row.value())));
            rank++;
        }
    }

    private long readLastReset() {
        File file = new File(plugin.getDataFolder(), STATE_FILE);
        if (!file.exists()) {
            long now = System.currentTimeMillis();
            writeLastReset(now);
            return now;
        }
        return YamlConfiguration.loadConfiguration(file).getLong("last-reset", System.currentTimeMillis());
    }

    private void writeLastReset(long epochMillis) {
        plugin.getDataFolder().mkdirs();
        File file = new File(plugin.getDataFolder(), STATE_FILE);
        YamlConfiguration config = new YamlConfiguration();
        config.set("last-reset", epochMillis);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning(STATE_FILE + " 저장 실패: " + e.getMessage());
        }
    }
}
