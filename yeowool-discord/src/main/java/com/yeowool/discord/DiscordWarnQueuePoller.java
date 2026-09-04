package com.yeowool.discord;

import com.yeowool.admin.moderation.AutoBanEscalation;
import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Discord → game direction of 경고 sync: periodically checks {@code
 * yw_discord_warn_queue} (written to by the JS bot's {@code /경고} slash
 * command) for unprocessed rows and applies each through the exact same
 * {@link YeowoolCoreAPI#punishments()} call {@code /경고 지급} itself uses —
 * including the same auto-ban escalation {@link WarnCommand#grant} performs —
 * rather than duplicating any of that logic.
 */
final class DiscordWarnQueuePoller extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final DiscordWarnQueueRepository repository;
    private final ExecutorService executor;
    private final int autoBanThreshold;
    private final long autoBanDurationMinutes;

    DiscordWarnQueuePoller(JavaPlugin plugin, YeowoolCoreAPI core, ExecutorService executor,
                            int autoBanThreshold, long autoBanDurationMinutes) {
        this.plugin = plugin;
        this.core = core;
        this.repository = new DiscordWarnQueueRepository(core.dataSource());
        this.executor = executor;
        this.autoBanThreshold = autoBanThreshold;
        this.autoBanDurationMinutes = autoBanDurationMinutes;
    }

    @Override
    public void run() {
        executor.execute(() -> {
            try {
                for (DiscordWarnQueueRepository.QueuedWarn queued : repository.findUnprocessed()) {
                    Bukkit.getScheduler().runTask(plugin, () -> process(queued));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("디스코드 경고 큐 조회 실패: " + e.getMessage());
            }
        });
    }

    private void process(DiscordWarnQueueRepository.QueuedWarn queued) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(queued.targetName());
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            markProcessed(queued.id(), "실패: 플레이어를 찾을 수 없음 (" + queued.targetName() + ")");
            return;
        }
        UUID targetId = target.getUniqueId();
        if ("REVOKE".equals(queued.action())) {
            processRevoke(queued, targetId, target);
        } else {
            processGrant(queued, targetId, target);
        }
    }

    private void processGrant(DiscordWarnQueueRepository.QueuedWarn queued, UUID targetId, OfflinePlayer target) {
        String reason = "[디스코드: " + queued.requestedBy() + "] " + queued.reason();
        core.punishments().recordWarning(targetId, reason, null, queued.points(), null).thenAccept(total ->
                AutoBanEscalation.checkAndApplyAutoBan(core, targetId, total, autoBanThreshold, autoBanDurationMinutes, null)
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            Player online = target.getPlayer();
                            if (online != null) {
                                if (total >= autoBanThreshold) {
                                    online.kick(net.kyori.adventure.text.Component.text(
                                            "경고 누적으로 자동 정지되었습니다. (누적 " + total + "회)"));
                                } else {
                                    online.sendMessage("§6[디스코드] §f" + queued.requestedBy() + "님이 경고 " + queued.points()
                                            + "점을 지급했습니다: " + queued.reason());
                                }
                            }
                            markProcessed(queued.id(), "지급 성공 (누적 " + total + "점)");
                        })));
    }

    /** Mirrors {@code WarnCommand.revoke()}: refuses (but still checks for a lift) if there aren't enough points left to take back. */
    private void processRevoke(DiscordWarnQueueRepository.QueuedWarn queued, UUID targetId, OfflinePlayer target) {
        core.punishments().totalWarningPoints(targetId).thenAccept(current -> {
            if (queued.points() > current) {
                AutoBanEscalation.checkAndLiftAutoBan(core, targetId, current, autoBanThreshold, null)
                        .thenRun(() -> markProcessed(queued.id(), "실패: 회수할 경고가 부족함 (현재 " + current + "점)"));
                return;
            }
            String reason = "[디스코드: " + queued.requestedBy() + "] " + queued.reason();
            core.punishments().recordWarning(targetId, reason, null, -queued.points(), null).thenAccept(total ->
                    AutoBanEscalation.checkAndLiftAutoBan(core, targetId, total, autoBanThreshold, null)
                            .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                                Player online = target.getPlayer();
                                if (online != null) {
                                    online.sendMessage("§6[디스코드] §f" + queued.requestedBy() + "님이 경고 " + queued.points()
                                            + "점을 회수했습니다: " + queued.reason());
                                }
                                markProcessed(queued.id(), "회수 성공 (누적 " + total + "점)");
                            })));
        });
    }

    private void markProcessed(long id, String result) {
        executor.execute(() -> {
            try {
                repository.markProcessed(id, result);
            } catch (SQLException e) {
                plugin.getLogger().warning("디스코드 경고 큐 처리 결과 기록 실패: " + e.getMessage());
            }
        });
    }
}
