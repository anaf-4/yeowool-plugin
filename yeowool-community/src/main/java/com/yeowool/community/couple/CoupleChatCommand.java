package com.yeowool.community.couple;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** {@code /커플채팅 <메시지>} — always targets your current partner, no name needed (unlike {@code /귓속말}). */
public final class CoupleChatCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final CoupleManager coupleManager;
    private final MessageService messages;

    public CoupleChatCommand(JavaPlugin plugin, YeowoolCoreAPI core, CoupleManager coupleManager, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.coupleManager = coupleManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "couple.chat-usage");
            return true;
        }
        var partnerId = coupleManager.partnerOf(player.getUniqueId());
        if (partnerId.isEmpty()) {
            messages.send(player, "couple.not-partnered");
            return true;
        }
        Player partner = Bukkit.getPlayer(partnerId.get());
        if (partner == null) {
            messages.send(player, "couple.partner-offline");
            return true;
        }
        String message = String.join(" ", args);
        core.punishments().activeMute(player.getUniqueId()).thenAccept(mute ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (mute.isPresent()) {
                        messages.send(player, "chat.muted");
                        return;
                    }
                    messages.send(partner, "couple.chat-received", Placeholder.unparsed("from", player.getName()), Placeholder.unparsed("message", message));
                    messages.send(player, "couple.chat-sent", Placeholder.unparsed("to", partner.getName()), Placeholder.unparsed("message", message));
                }));
        return true;
    }
}
