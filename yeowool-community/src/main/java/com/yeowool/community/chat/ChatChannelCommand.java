package com.yeowool.community.chat;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /채널 전체|지역|마을} — changes which channel a player's normal chat goes to. */
public final class ChatChannelCommand implements CommandExecutor, TabCompleter {

    private final ChatChannelService channelService;
    private final MessageService messages;

    public ChatChannelCommand(ChatChannelService channelService, MessageService messages) {
        this.channelService = channelService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "chat.channel-usage");
            return true;
        }
        ChatChannelService.Channel channel = switch (args[0]) {
            case "전체" -> ChatChannelService.Channel.GLOBAL;
            case "지역" -> ChatChannelService.Channel.LOCAL;
            case "마을" -> ChatChannelService.Channel.LAND;
            default -> null;
        };
        if (channel == null) {
            messages.send(player, "chat.channel-usage");
            return true;
        }
        if (channel == ChatChannelService.Channel.LAND && !channelService.hasLand(player)) {
            messages.send(player, "chat.no-land");
            return true;
        }
        channelService.setChannel(player, channel);
        messages.send(player, "chat.channel-set", Placeholder.unparsed("channel", args[0]));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("전체", "지역", "마을"), args[0]);
        }
        return List.of();
    }
}
