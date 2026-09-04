package com.yeowool.admin.moderation;

import com.yeowool.admin.itemtool.DurationParser;
import com.yeowool.core.api.YeowoolCoreAPI;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** {@code /음소거 <닉네임> <기간|영구> <사유>} — enforced by YeowoolCommunity's chat listener. */
public final class MuteCommand implements CommandExecutor, TabCompleter {

    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public MuteCommand(YeowoolCoreAPI core, MessageService messages) {
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "moderation.mute-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(sender, "general.player-not-found");
            return true;
        }

        Long expiresAt;
        if (args[1].equals("영구")) {
            expiresAt = null;
        } else {
            long durationMs = DurationParser.parseToMillis(args[1]);
            if (durationMs <= 0) {
                messages.send(sender, "moderation.duration-invalid");
                return true;
            }
            expiresAt = System.currentTimeMillis() + durationMs;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        UUID staff = sender instanceof Player player ? player.getUniqueId() : null;

        core.punishments().record(target.getUniqueId(), PunishmentType.MUTE, reason, staff, expiresAt);
        messages.send(sender, "moderation.mute-success",
                Placeholder.unparsed("target", args[0]),
                Placeholder.unparsed("duration", expiresAt == null ? "영구" : args[1]),
                Placeholder.unparsed("reason", reason));

        Player online = target.getPlayer();
        if (online != null) {
            messages.send(online, "moderation.mute-notify", Placeholder.unparsed("reason", reason));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        if (args.length == 2) {
            return TabCompletions.filter(List.of("영구", "10m", "1h", "1d", "7d"), args[1]);
        }
        return List.of();
    }
}
