package com.yeowool.admin.moderation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** {@code /제재기록 <닉네임> [개수]} — full warn/kick/mute/ban history for one player. */
public final class PunishmentHistoryCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public PunishmentHistoryCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "moderation.history-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            messages.send(sender, "general.player-not-found");
            return true;
        }
        int limit = 20;
        if (args.length >= 2) {
            try {
                limit = Math.max(1, Math.min(100, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                messages.send(sender, "general.invalid-count");
                return true;
            }
        }

        int finalLimit = limit;
        core.punishments().history(target.getUniqueId(), finalLimit).thenAccept(entries ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (entries.isEmpty()) {
                        messages.send(sender, "moderation.history-empty", Placeholder.unparsed("target", args[0]));
                        return;
                    }
                    messages.send(sender, "moderation.history-header",
                            Placeholder.unparsed("target", args[0]),
                            Placeholder.unparsed("count", String.valueOf(entries.size())));
                    for (var entry : entries) {
                        String time = FORMAT.format(Instant.ofEpochMilli(entry.createdAt()));
                        String staffName = entry.staff() == null ? "콘솔" : String.valueOf(Bukkit.getOfflinePlayer(entry.staff()).getName());
                        String status = !entry.active() ? "해제됨" : (entry.isExpired() ? "만료됨" : "적용 중");
                        String expiry = entry.isPermanent() ? "영구" : FORMAT.format(Instant.ofEpochMilli(entry.expiresAt()));
                        messages.send(sender, "moderation.history-line",
                                Placeholder.unparsed("time", time),
                                Placeholder.unparsed("type", entry.type().toString()),
                                Placeholder.unparsed("reason", entry.reason()),
                                Placeholder.unparsed("staff", staffName),
                                Placeholder.unparsed("expiry", expiry),
                                Placeholder.unparsed("status", status));
                    }
                }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
