package com.yeowool.admin.moderation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The auto-ban threshold check shared by every place a warning's cumulative
 * total can change — originally inline in {@link WarnCommand#grant}/{@link
 * WarnCommand#revoke}, extracted here once {@code
 * com.yeowool.discord.DiscordWarnQueuePoller} needed the exact same
 * behavior for Discord-originated grants/revokes (previously it only
 * applied the grant half, never lifting a ban on a Discord-issued revoke —
 * the gap this class closes).
 */
public final class AutoBanEscalation {

    private AutoBanEscalation() {
    }

    /** If {@code total} has crossed the threshold, records the escalation BAN — same as every prior threshold-crossing grant does (each one gets its own row, matching existing behavior). */
    public static CompletableFuture<Void> checkAndApplyAutoBan(YeowoolCoreAPI core, UUID targetId, int total,
                                                                 int autoBanThreshold, long autoBanDurationMinutes, UUID staff) {
        if (total < autoBanThreshold) {
            return CompletableFuture.completedFuture(null);
        }
        Long banExpiresAt = autoBanDurationMinutes > 0 ? System.currentTimeMillis() + autoBanDurationMinutes * 60_000L : null;
        return core.punishments().record(targetId, PunishmentType.BAN,
                WarnCommand.AUTOBAN_REASON_PREFIX + " (누적 " + total + "회)", staff, banExpiresAt);
    }

    /**
     * If {@code total} has dropped back under the threshold and an active
     * ban stamped with {@link WarnCommand#AUTOBAN_REASON_PREFIX} still
     * exists, lifts it. Only ever touches a ban this same escalation
     * created — a staff-issued {@code /정지} for an unrelated reason is
     * never affected. Returns whether it actually lifted one.
     */
    public static CompletableFuture<Boolean> checkAndLiftAutoBan(YeowoolCoreAPI core, UUID targetId, int total,
                                                                    int autoBanThreshold, UUID staff) {
        if (total >= autoBanThreshold) {
            return CompletableFuture.completedFuture(false);
        }
        return core.punishments().activeBan(targetId).thenCompose(activeBan -> {
            if (activeBan.isPresent() && activeBan.get().reason().startsWith(WarnCommand.AUTOBAN_REASON_PREFIX)) {
                return core.punishments().revoke(targetId, PunishmentType.BAN, staff).thenApply(count -> count > 0);
            }
            return CompletableFuture.completedFuture(false);
        });
    }
}
