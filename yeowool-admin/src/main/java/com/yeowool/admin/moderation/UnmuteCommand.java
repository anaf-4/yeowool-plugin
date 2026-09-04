package com.yeowool.admin.moderation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentType;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** {@code /음소거해제 <닉네임>} — deactivates every currently-active mute for that target. */
public final class UnmuteCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public UnmuteCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            messages.send(sender, "moderation.unmute-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            messages.send(sender, "general.player-not-found");
            return true;
        }
        UUID staff = sender instanceof Player player ? player.getUniqueId() : null;
        core.punishments().revoke(target.getUniqueId(), PunishmentType.MUTE, staff).thenAccept(count ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (count > 0) {
                        messages.send(sender, "moderation.unmute-success", Placeholder.unparsed("target", args[0]));
                    } else {
                        messages.send(sender, "moderation.unmute-not-active");
                    }
                }));
        return true;
    }
}
