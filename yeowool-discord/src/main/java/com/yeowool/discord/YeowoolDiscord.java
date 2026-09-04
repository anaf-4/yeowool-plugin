package com.yeowool.discord;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.discord.database.DiscordSchemaInitializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Paper-side half of the Discord integration. Doesn't talk to Discord
 * itself at all (no HTTP calls out, no bot library) — it just:
 * <ol>
 *     <li>Pushes this plugin's {@code config.yml} into {@code
 *     yw_discord_config} ({@link DiscordConfigSync}) so the standalone JS
 *     bot (running on whatever PC it's deployed to) can read its settings
 *     from the same MySQL database it already needs.</li>
 *     <li>Polls {@code yw_discord_warn_queue} ({@link DiscordWarnQueuePoller})
 *     for 경고 requests the bot's {@code /경고} slash command queued, and
 *     applies them through the real {@code /경고 지급} code path.</li>
 * </ol>
 * The game→Discord direction (경고 log relay, 공지 relay) needs no code here
 * at all — {@code /경고 지급} already writes to {@code yw_punishments}, and
 * {@code AdminCommand.announce()} writes to {@code yw_discord_announcements}
 * directly; the bot just polls those tables itself.
 */
public final class YeowoolDiscord extends JavaPlugin {

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
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "YeowoolDiscord-Worker");
            thread.setDaemon(true);
            return thread;
        });

        try {
            DiscordSchemaInitializer.initialize(core.dataSource());
        } catch (Exception e) {
            getLogger().severe("디스코드 연동 테이블 초기화 실패: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pushConfig(core);
        var reloadCommand = getCommand("디스코드리로드");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(reloadExecutor(core));
        }

        int autoBanThreshold = getConfig().getInt("moderation.auto-ban-threshold", 10);
        long autoBanDurationMinutes = getConfig().getLong("moderation.auto-ban-duration-minutes", 1440);
        long pollIntervalTicks = getConfig().getLong("warn-queue-poll-interval-ticks", 100L);
        if (pollIntervalTicks > 0) {
            new DiscordWarnQueuePoller(this, core, executor, autoBanThreshold, autoBanDurationMinutes)
                    .runTaskTimer(this, pollIntervalTicks, pollIntervalTicks);
        }

        getLogger().info("YeowoolDiscord가 활성화되었습니다. (실제 디스코드 접속은 별도 JS 봇이 담당)");
    }

    private void pushConfig(YeowoolCoreAPI core) {
        executor.execute(() -> {
            try {
                DiscordConfigSync.push(core.dataSource(), getConfig(), this);
            } catch (Exception e) {
                getLogger().warning("디스코드 설정을 DB에 반영하지 못했습니다: " + e.getMessage());
            }
        });
    }

    private CommandExecutor reloadExecutor(YeowoolCoreAPI core) {
        return (CommandSender sender, Command command, String label, String[] args) -> {
            reloadConfig();
            pushConfig(core);
            sender.sendMessage("§a디스코드 설정을 DB에 다시 반영했습니다. (봇이 다음 확인 주기에 반영합니다)");
            return true;
        };
    }

    @Override
    public void onDisable() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
