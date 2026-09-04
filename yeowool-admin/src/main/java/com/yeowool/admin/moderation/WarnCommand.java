package com.yeowool.admin.moderation;

import com.yeowool.admin.itemtool.DurationParser;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentEntry;
import com.yeowool.core.api.model.PunishmentType;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /경고 지급|회수|기록} — unlike {@code /추방}/{@code /정지} (one row = one
 * effect), a warning's effect is its running point total: each 지급/회수 is its
 * own permanent row carrying a signed point delta (see {@link
 * com.yeowool.core.api.model.PunishmentEntry#points}), and the total across
 * every WARN row is what {@code autoBanThreshold} compares against. Crossing
 * it records an actual {@link PunishmentType#BAN} (not just a kick) — a
 * currently-connected session is also kicked immediately for the same
 * effect, but recording the ban itself (regardless of whether the target is
 * online right now) is what actually stops them from walking back in, since
 * {@code PlayerConnectionListener}'s pre-login check enforces any active ban
 * the same way it does for {@code /정지}.
 */
public final class WarnCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    /**
     * Reason prefix stamped on a BAN created by the auto-ban escalation, so
     * it can be told apart from a staff-issued /정지 before lifting it —
     * here, in {@link WarnAutoBanReviewTask}'s periodic sweep, and in {@code
     * com.yeowool.admin.discord.DiscordWarnQueuePoller} (public for that
     * last one, since it lives in a different package).
     */
    public static final String AUTOBAN_REASON_PREFIX = "경고 누적 자동 정지";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final int autoBanThreshold;
    private final long autoBanDurationMinutes;

    /** {@code autoBanDurationMinutes} of 0 or less means the auto-ban is permanent. */
    public WarnCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, int autoBanThreshold, long autoBanDurationMinutes) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.autoBanThreshold = autoBanThreshold;
        this.autoBanDurationMinutes = autoBanDurationMinutes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "warn.root-usage");
            return true;
        }
        switch (args[0]) {
            case "지급" -> grant(sender, args);
            case "회수" -> revoke(sender, args);
            case "기록" -> history(sender, args);
            default -> messages.send(sender, "warn.invalid-subcommand");
        }
        return true;
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "warn.grant-usage");
            return;
        }
        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }

        // An optional trailing duration ("7d", "24h", "영구") after the count lets
        // staff set when this specific grant's points stop counting. A bare
        // number is never treated as a duration here (unlike DurationParser's
        // normal "bare number = minutes" convention) since that would be
        // ambiguous with the required 경고 수 argument right before it.
        boolean hasDuration = args.length >= 5 && looksLikeDuration(args[args.length - 1]);
        int countIndex = hasDuration ? args.length - 2 : args.length - 1;

        int points = parsePositiveCount(sender, args[countIndex]);
        if (points < 0) {
            return;
        }
        Long expiresAt = null;
        if (hasDuration && !args[args.length - 1].equals("영구")) {
            long durationMs = DurationParser.parseToMillis(args[args.length - 1]);
            if (durationMs <= 0) {
                messages.send(sender, "moderation.duration-invalid");
                return;
            }
            expiresAt = System.currentTimeMillis() + durationMs;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, countIndex));
        UUID staff = sender instanceof Player player ? player.getUniqueId() : null;
        UUID targetId = target.getUniqueId();
        String targetName = args[1];
        String expiryText = expiresAt == null ? "영구" : FORMAT.format(Instant.ofEpochMilli(expiresAt));

        core.punishments().recordWarning(targetId, reason, staff, points, expiresAt).thenAccept(total -> {
            boolean crossedThreshold = total >= autoBanThreshold;
            AutoBanEscalation.checkAndApplyAutoBan(core, targetId, total, autoBanThreshold, autoBanDurationMinutes, staff)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        messages.send(sender, "warn.grant-success",
                                Placeholder.unparsed("target", targetName),
                                Placeholder.unparsed("points", String.valueOf(points)),
                                Placeholder.unparsed("reason", reason),
                                Placeholder.unparsed("total", String.valueOf(total)),
                                Placeholder.unparsed("expiry", expiryText));

                        Player online = target.getPlayer();
                        if (online != null) {
                            messages.send(online, "warn.grant-notify",
                                    Placeholder.unparsed("points", String.valueOf(points)),
                                    Placeholder.unparsed("reason", reason));
                        }

                        if (crossedThreshold) {
                            Long banExpiresAt = autoBanDurationMinutes > 0 ? System.currentTimeMillis() + autoBanDurationMinutes * 60_000L : null;
                            String banExpiryText = banExpiresAt == null ? "영구 정지" : "정지 만료: " + FORMAT.format(Instant.ofEpochMilli(banExpiresAt));
                            if (online != null) {
                                online.kick(messages.resolveRaw("warn.autoban-kick",
                                        Placeholder.unparsed("threshold", String.valueOf(autoBanThreshold)),
                                        Placeholder.unparsed("expiry", banExpiryText)));
                            }
                            messages.send(sender, "warn.autoban-notify",
                                    Placeholder.unparsed("target", targetName),
                                    Placeholder.unparsed("total", String.valueOf(total)),
                                    Placeholder.unparsed("expiry", banExpiryText));
                        }
                    }));
        });
    }

    /** True for "영구" or a duration string with at least one unit suffix (e.g. "7d", "1d12h") — never a bare number, see {@link #grant}. */
    private static boolean looksLikeDuration(String token) {
        return token.equals("영구") || token.matches("(?i)(\\d+[smhd])+");
    }

    private void revoke(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "warn.revoke-usage");
            return;
        }
        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        int points = parsePositiveCount(sender, args[args.length - 1]);
        if (points < 0) {
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
        UUID staff = sender instanceof Player player ? player.getUniqueId() : null;
        UUID targetId = target.getUniqueId();
        String targetName = args[1];

        core.punishments().totalWarningPoints(targetId).thenAccept(current ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (points > current) {
                        // Not enough points left to revoke — often because they already
                        // expired on their own (see WarnCommand's 만료기간 option) after an
                        // auto-ban had already fired. That ban doesn't share the warning's
                        // expiry, so it's still sitting there active even though the count
                        // that caused it is already back under the threshold — check for
                        // that and lift it here too, not just after a *successful* revoke,
                        // or an admin with nothing left to revoke would have no way to ever
                        // trigger the check.
                        messages.send(sender, "warn.revoke-insufficient", Placeholder.unparsed("current", String.valueOf(current)));
                        checkAndLiftAutoBan(sender, targetId, targetName, staff, current);
                        return;
                    }
                    core.punishments().recordWarning(targetId, reason, staff, -points, null).thenAccept(total ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                messages.send(sender, "warn.revoke-success",
                                        Placeholder.unparsed("target", targetName),
                                        Placeholder.unparsed("points", String.valueOf(points)),
                                        Placeholder.unparsed("reason", reason),
                                        Placeholder.unparsed("total", String.valueOf(total)));
                                Player online = target.getPlayer();
                                if (online != null) {
                                    messages.send(online, "warn.revoke-notify",
                                            Placeholder.unparsed("points", String.valueOf(points)),
                                            Placeholder.unparsed("reason", reason));
                                }
                                checkAndLiftAutoBan(sender, targetId, targetName, staff, total);
                            }));
                }));
    }

    /**
     * Lifts the BAN the auto-ban escalation itself created, if {@code total}
     * is under the threshold and one is still active — regardless of *why*
     * the total dropped (an explicit 회수, or a grant's own 만료기간 lapsing
     * on its own). Only a ban stamped with our own AUTOBAN_REASON_PREFIX is
     * ever touched, so a staff-issued /정지 for an unrelated reason is never
     * lifted just because someone's warning count happened to drop.
     */
    private void checkAndLiftAutoBan(CommandSender sender, UUID targetId, String targetName, UUID staff, int total) {
        AutoBanEscalation.checkAndLiftAutoBan(core, targetId, total, autoBanThreshold, staff).thenAccept(lifted ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (lifted) {
                        messages.send(sender, "warn.autoban-lifted", Placeholder.unparsed("target", targetName));
                    }
                }));
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length != 2) {
            messages.send(sender, "warn.history-usage");
            return;
        }
        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        String targetName = args[1];
        UUID targetId = target.getUniqueId();

        // totalWarningPoints() (not a local re-sum of the fetched rows) is the
        // authoritative count — it's the same query the auto-ban check itself
        // uses, so the header here can never disagree with what actually
        // decides whether someone gets banned (an expired grant's points are
        // excluded there, but the row still shows below for the audit trail).
        core.punishments().totalWarningPoints(targetId).thenAccept(total ->
                core.punishments().history(targetId, 100).thenAccept(entries ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            List<PunishmentEntry> warnings = entries.stream().filter(e -> e.type() == PunishmentType.WARN).toList();
                            if (warnings.isEmpty()) {
                                messages.send(sender, "warn.history-empty", Placeholder.unparsed("target", targetName));
                                return;
                            }
                            messages.send(sender, "warn.history-header",
                                    Placeholder.unparsed("target", targetName),
                                    Placeholder.unparsed("total", String.valueOf(total)));
                            for (PunishmentEntry entry : warnings) {
                                String time = FORMAT.format(Instant.ofEpochMilli(entry.createdAt()));
                                String staffName = entry.staff() == null ? "콘솔" : String.valueOf(Bukkit.getOfflinePlayer(entry.staff()).getName());
                                String delta = entry.points() >= 0 ? "+" + entry.points() : String.valueOf(entry.points());
                                String expiry = entry.points() <= 0 ? ""
                                        : entry.isPermanent() ? " (영구)"
                                        : entry.isExpired() ? " (만료됨: " + FORMAT.format(Instant.ofEpochMilli(entry.expiresAt())) + ")"
                                        : " (만료: " + FORMAT.format(Instant.ofEpochMilli(entry.expiresAt())) + ")";
                                String key = entry.points() >= 0 ? "warn.history-line-positive" : "warn.history-line-negative";
                                messages.send(sender, key,
                                        Placeholder.unparsed("time", time),
                                        Placeholder.unparsed("delta", delta),
                                        Placeholder.unparsed("reason", entry.reason()),
                                        Placeholder.unparsed("staff", staffName),
                                        Placeholder.unparsed("expiry", expiry));
                            }
                        })));
    }

    private OfflinePlayer resolveTarget(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(sender, "general.player-not-found");
            return null;
        }
        return target;
    }

    /** Returns -1 (and already messaged the sender) if not a positive integer. */
    private int parsePositiveCount(CommandSender sender, String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                messages.send(sender, "warn.count-invalid");
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            messages.send(sender, "warn.count-invalid");
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("지급", "회수", "기록"), args[0]);
        }
        if (args.length == 2) {
            return TabCompletions.filteredOnlinePlayerNames(args[1]);
        }
        return List.of();
    }
}
