package com.yeowool.community.chat;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** {@code /답장 <메시지>} — replies to whoever last whispered this player. */
public final class ReplyCommand implements CommandExecutor {

    private final WhisperCommand whisperCommand;
    private final MessageService messages;

    public ReplyCommand(WhisperCommand whisperCommand, MessageService messages) {
        this.whisperCommand = whisperCommand;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "chat.reply-usage");
            return true;
        }
        UUID lastFrom = whisperCommand.lastWhisperFrom(player.getUniqueId());
        if (lastFrom == null) {
            messages.send(player, "chat.no-recent-whisper");
            return true;
        }
        Player target = Bukkit.getPlayer(lastFrom);
        if (target == null) {
            messages.send(player, "chat.player-not-found");
            return true;
        }
        whisperCommand.sendWhisper(player, target, String.join(" ", args));
        return true;
    }
}
