package com.yeowool.admin.report;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /신고 <닉네임> <사유>} — a lightweight reporting channel open to
 * every player (no permission node), so grief/abuse reaches staff without
 * needing a Discord bridge. Broadcasts to whoever holds
 * {@code yeowool.admin.alerts} and writes a permanent {@code yw_logs} trail
 * so `/여울관리 로그 <신고자>` shows report history too.
 */
public final class ReportCommand implements CommandExecutor, TabCompleter {

    private static final long COOLDOWN_MILLIS = 60_000L;

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Map<UUID, Long> lastReportAt = new ConcurrentHashMap<>();

    public ReportCommand(YeowoolCoreAPI core, MessageService messages) {
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "report.usage");
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = lastReportAt.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            long remainingSeconds = (COOLDOWN_MILLIS - (now - last)) / 1000 + 1;
            messages.send(player, "report.cooldown", Placeholder.unparsed("seconds", String.valueOf(remainingSeconds)));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(player, "general.player-not-found");
            return true;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        lastReportAt.put(player.getUniqueId(), now);
        messages.send(player, "report.submitted");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yeowool.admin.alerts")) {
                messages.send(staff, "report.staff-alert",
                        Placeholder.unparsed("reporter", player.getName()),
                        Placeholder.unparsed("target", args[0]),
                        Placeholder.unparsed("reason", reason));
            }
        }

        core.logs().log("YeowoolAdmin", "report.player-report", player.getUniqueId(),
                "플레이어 신고: " + args[0], Map.of("target", args[0], "reason", reason));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return com.yeowool.core.util.TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
