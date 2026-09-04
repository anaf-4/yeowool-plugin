package com.yeowool.analytics;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.analytics.command.RankingCommand;
import com.yeowool.analytics.command.StatsCommand;
import com.yeowool.analytics.season.SeasonManager;
import com.yeowool.analytics.season.SeasonRankingCommand;
import com.yeowool.analytics.season.SeasonRepository;
import com.yeowool.analytics.season.SeasonScoreListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 서버/경제/토지/생활 통계 (기획서 11.2). 대시보드 없이 {@code /여울통계}
 * 명령어로 즉시 집계 결과를 보여주는 것으로 범위를 좁혔다 — 실시간
 * 웹 대시보드는 이후 확장 과제로 남긴다.
 */
public final class YeowoolAnalytics extends JavaPlugin {

    private ExecutorService executor;

    @Override
    public void onEnable() {
        YeowoolCoreAPI core = Bukkit.getServicesManager().load(YeowoolCoreAPI.class);
        if (core == null) {
            getLogger().severe("YeowoolCore API를 찾을 수 없습니다. YeowoolCore가 먼저 로드되어야 합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigMerger.mergeDefaults(this, "config.yml");
        MessageService messages = new MessageManager(this);

        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolAnalytics-Worker");
            thread.setDaemon(true);
            return thread;
        });

        MetricsCollector collector = new MetricsCollector(core.dataSource());
        var command = getCommand("여울통계");
        if (command != null) {
            command.setExecutor(new StatsCommand(this, messages, collector, executor));
        }

        var rankingCommand = getCommand("랭킹");
        if (rankingCommand != null) {
            var executorCmd = new RankingCommand(this, messages, collector, executor);
            rankingCommand.setExecutor(executorCmd);
            rankingCommand.setTabCompleter(executorCmd);
        }

        getServer().getPluginManager().registerEvents(new SeasonScoreListener(core), this);
        SeasonRepository seasonRepository = new SeasonRepository(core.dataSource());
        Map<Integer, Long> rewardTiers = new LinkedHashMap<>();
        var tiersSection = getConfig().getConfigurationSection("season.reward-tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                try {
                    rewardTiers.put(Integer.parseInt(key), tiersSection.getLong(key));
                } catch (NumberFormatException e) {
                    getLogger().warning("season.reward-tiers 키가 숫자가 아닙니다: " + key);
                }
            }
        }
        SeasonManager seasonManager = new SeasonManager(this, core, messages, collector, seasonRepository, executor,
                getConfig().getInt("season.period-days", 7), rewardTiers, getConfig().getBoolean("season.broadcast", true));

        var seasonRankingCommand = getCommand("시즌랭킹");
        if (seasonRankingCommand != null) {
            seasonRankingCommand.setExecutor(new SeasonRankingCommand(this, messages, collector, seasonManager, executor));
        }
        // 시즌이 끝났는지는 1시간마다 확인 - 실제로 끝났을 때만 SeasonManager 내부에서 동작함
        Bukkit.getScheduler().runTaskTimer(this, seasonManager::checkAndRunIfDue, 20L * 60, 20L * 60 * 60);

        getLogger().info("YeowoolAnalytics가 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
