package com.yeowool.admin.moderation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentEntry;
import com.yeowool.core.api.model.PunishmentType;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Periodically re-checks every currently active warning-triggered auto-ban
 * (see {@link WarnCommand#AUTOBAN_REASON_PREFIX}) against its target's
 * current warning total, lifting any where the total has since dropped back
 * under the threshold. This exists because a grant's own 만료기간 (see
 * {@code WarnCommand.grant}) can lapse on its own with nobody ever running
 * {@code /경고 회수} afterward — {@code WarnCommand.revoke}'s check only ever
 * runs when an admin actually issues a 회수, so without this sweep a player
 * whose points simply expired would stay banned until the ban's own
 * (separate, config-driven) expiry, which could be far longer or even
 * permanent depending on {@code moderation.warning.auto-ban-duration-minutes}.
 */
public final class WarnAutoBanReviewTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final int autoBanThreshold;

    public WarnAutoBanReviewTask(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, int autoBanThreshold) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.autoBanThreshold = autoBanThreshold;
    }

    @Override
    public void run() {
        core.punishments().findActiveByReasonPrefix(PunishmentType.BAN, WarnCommand.AUTOBAN_REASON_PREFIX).thenAccept(activeBans -> {
            if (activeBans.isEmpty()) {
                return;
            }
            var targets = activeBans.stream().map(PunishmentEntry::target).collect(Collectors.toSet());
            // One batched lookup instead of one totalWarningPoints() query per active
            // ban — this sweep runs periodically over every such ban server-wide, so
            // that would otherwise be an avoidable N+1 query pattern.
            core.punishments().totalWarningPointsForTargets(targets).thenAccept(totals ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (UUID target : targets) {
                            int total = totals.getOrDefault(target, 0);
                            if (total < autoBanThreshold) {
                                lift(target);
                            }
                        }
                    }));
        });
    }

    private void lift(UUID targetId) {
        core.punishments().revoke(targetId, PunishmentType.BAN, null);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        plugin.getLogger().info("경고 누적이 임계치 미만으로 줄어들어 자동 정지를 해제했습니다: " + target.getName());
        for (var staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yeowool.admin.alerts")) {
                messages.send(staff, "warn.autoban-review-lifted", Placeholder.unparsed("target", String.valueOf(target.getName())));
            }
        }
    }
}
