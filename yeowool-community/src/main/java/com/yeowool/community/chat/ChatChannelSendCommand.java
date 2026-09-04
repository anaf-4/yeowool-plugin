package com.yeowool.community.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** {@code /전체}, {@code /지역}, {@code /마을} — sends one message on that channel without changing the player's default channel. */
public final class ChatChannelSendCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final ChatChannelService channelService;
    private final ChatChannelService.Channel channel;
    private final MessageService messages;

    public ChatChannelSendCommand(JavaPlugin plugin, YeowoolCoreAPI core, ChatChannelService channelService,
                                   ChatChannelService.Channel channel, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.channelService = channelService;
        this.channel = channel;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "chat.send-usage");
            return true;
        }
        if (channel == ChatChannelService.Channel.LAND && !channelService.hasLand(player)) {
            messages.send(player, "chat.no-land");
            return true;
        }
        String message = String.join(" ", args);
        core.punishments().activeMute(player.getUniqueId()).thenAccept(mute ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (mute.isPresent()) {
                        messages.send(player, "chat.muted");
                        return;
                    }
                    channelService.send(player, channel, message);
                }));
        return true;
    }
}
