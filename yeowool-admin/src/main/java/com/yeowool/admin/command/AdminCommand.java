package com.yeowool.admin.command;

import com.yeowool.admin.backup.BackupService;
import com.yeowool.admin.logviewer.LogQueryService;
import com.yeowool.admin.logviewer.LogViewerGui;
import com.yeowool.admin.starterkit.StarterKitEditorGui;
import com.yeowool.admin.starterkit.StarterKitService;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * {@code /여울관리} — section 11.1's integrated admin command, scoped to the
 * subset that's implementable without every other plugin's admin surface
 * (플레이어/경제/토지/상점 관리 in the full plan would extend this further
 * as those plugins grow their own admin needs).
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final BackupService backupService;
    private final StarterKitService starterKitService;
    private final LogQueryService logQueryService;
    private final ExecutorService executor;

    public AdminCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, BackupService backupService,
                         StarterKitService starterKitService, LogQueryService logQueryService, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.backupService = backupService;
        this.starterKitService = starterKitService;
        this.logQueryService = logQueryService;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "admin.usage", Placeholder.unparsed("label", label));
            return true;
        }

        switch (args[0]) {
            case "정보" -> info(sender);
            case "지급" -> grant(sender, args);
            case "공지" -> announce(sender, args);
            case "백업" -> backup(sender);
            case "복구" -> restore(sender, args);
            case "기본템" -> editStarterKit(sender);
            case "로그" -> viewLogs(sender, args);
            case "우편" -> mail(sender, args);
            default -> messages.send(sender, "admin.unknown-subcommand");
        }
        return true;
    }

    private void info(CommandSender sender) {
        double tps = Bukkit.getServer().getTPS()[0];
        long uptimeMinutes = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 60000;
        messages.send(sender, "admin.info",
                Placeholder.unparsed("tps", String.format("%.2f", tps)),
                Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())),
                Placeholder.unparsed("uptime", String.valueOf(uptimeMinutes)));
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length != 3) {
            messages.send(sender, "admin.grant-usage");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "general.invalid-amount");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null) {
            messages.send(sender, "general.player-not-found");
            return;
        }
        core.playerData().load(target.getUniqueId(), args[1]).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            core.economyData().modifyBalance(target.getUniqueId(), amount, "YeowoolAdmin",
                    "관리자 지급 (" + sender.getName() + ")");
            messages.send(sender, "admin.grant-success",
                    Placeholder.unparsed("target", args[1]),
                    Placeholder.unparsed("amount", String.valueOf(amount)));
        }));
    }

    private void announce(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "admin.announce-usage");
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        messages.broadcast("admin.announce-broadcast", Placeholder.unparsed("message", message));

        // 디스코드 봇(yeowool-discord, 별도 PC에서 실행)이 이 테이블을 주기적으로 확인해서
        // 지정된 채널에 그대로 옮겨 올림 — 봇이 안 켜져 있어도 그냥 안 읽힌 행으로 남을 뿐,
        // 여기서 실패하거나 막히지 않음.
        executor.execute(() -> {
            try (var connection = core.dataSource().getConnection();
                 var insert = connection.prepareStatement(
                         "INSERT INTO yw_discord_announcements (message, created_at) VALUES (?, ?)")) {
                insert.setString(1, message);
                insert.setLong(2, System.currentTimeMillis());
                insert.executeUpdate();
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("디스코드 공지 큐 기록 실패: " + e.getMessage());
            }
        });
    }

    private void backup(CommandSender sender) {
        messages.send(sender, "admin.backup-start");
        executor.execute(() -> {
            try {
                var dir = backupService.runBackup();
                Bukkit.getScheduler().runTask(plugin, () ->
                        messages.send(sender, "admin.backup-success", Placeholder.unparsed("dir", String.valueOf(dir))));
            } catch (Exception e) {
                plugin.getLogger().severe("백업 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        messages.send(sender, "admin.backup-fail", Placeholder.unparsed("error", String.valueOf(e.getMessage()))));
            }
        });
    }

    private void restore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("yeowool.admin.super")) {
            messages.send(sender, "admin.restore-permission-denied");
            return;
        }
        if (args.length != 2) {
            messages.send(sender, "admin.restore-usage");
            return;
        }
        String folder = args[1];
        messages.send(sender, "admin.restore-start", Placeholder.unparsed("folder", folder));
        executor.execute(() -> {
            try {
                backupService.restore(folder);
                Bukkit.getScheduler().runTask(plugin, () ->
                        messages.send(sender, "admin.restore-success", Placeholder.unparsed("folder", folder)));
            } catch (Exception e) {
                plugin.getLogger().severe("복구 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        messages.send(sender, "admin.restore-fail", Placeholder.unparsed("error", String.valueOf(e.getMessage()))));
            }
        });
    }

    /**
     * Opens {@link com.yeowool.admin.logviewer.LogViewerGui} instead of
     * dumping chat lines — every row for the target (up to {@code 개수},
     * uncategorized) is fetched once, and the GUI's category filter/paging
     * both work purely in-memory off that one batch.
     */
    private void viewLogs(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "admin.logs-usage");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(sender, "general.player-not-found");
            return;
        }
        int limit = 200;
        if (args.length >= 3) {
            try {
                limit = Math.max(1, Math.min(500, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                messages.send(sender, "general.invalid-count");
                return;
            }
        }
        String targetName = target.getName() != null ? target.getName() : args[1];

        int finalLimit = limit;
        executor.execute(() -> {
            try {
                var rows = logQueryService.queryByActor(target.getUniqueId(), null, finalLimit);
                var categories = rows.stream().map(LogQueryService.LogRow::category).distinct().sorted().toList();
                Bukkit.getScheduler().runTask(plugin, () ->
                        new LogViewerGui(targetName, rows, categories, "전체", 0).open(admin));
            } catch (Exception e) {
                plugin.getLogger().severe("로그 조회 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        messages.send(sender, "admin.logs-query-fail", Placeholder.unparsed("error", String.valueOf(e.getMessage()))));
            }
        });
    }

    private void mail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "admin.mail-player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "admin.mail-usage");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            messages.send(sender, "admin.mail-no-item");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(sender, "general.player-not-found");
            return;
        }
        String note = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "관리자 지급 (" + sender.getName() + ")";

        ItemStack toSend = hand.clone();
        player.getInventory().setItemInMainHand(null);
        core.mailbox().deliverOrStore(target.getUniqueId(), toSend, "YeowoolAdmin", note);
        messages.send(sender, "admin.mail-success", Placeholder.unparsed("target", args[1]));
    }

    private void editStarterKit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        new StarterKitEditorGui(messages, starterKitService).open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("정보", "지급", "공지", "백업", "복구", "기본템", "로그", "우편"), args[0]);
        }
        if (args.length == 2 && (args[0].equals("지급") || args[0].equals("로그") || args[0].equals("우편"))) {
            return TabCompletions.filteredOnlinePlayerNames(args[1]);
        }
        if (args.length == 2 && args[0].equals("복구")) {
            try {
                return TabCompletions.filter(backupService.listBackups(), args[1]);
            } catch (java.io.IOException e) {
                return List.of();
            }
        }
        return List.of();
    }
}
