package com.yeowool.admin.moderation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentType;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** {@code /추방 <닉네임> [사유]} — kicks an online player immediately and records it. */
public final class KickCommand implements CommandExecutor, TabCompleter {

    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public KickCommand(YeowoolCoreAPI core, MessageService messages) {
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "moderation.kick-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(sender, "moderation.kick-not-online");
            return true;
        }
        String reason = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "사유 없음";
        UUID staff = sender instanceof Player player ? player.getUniqueId() : null;

        core.punishments().record(target.getUniqueId(), PunishmentType.KICK, reason, staff, null);
        target.kick(messages.resolveRaw("moderation.kick-notify", Placeholder.unparsed("reason", reason)));
        messages.send(sender, "moderation.kick-success",
                Placeholder.unparsed("target", args[0]),
                Placeholder.unparsed("reason", reason));
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
