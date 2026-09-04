package com.yeowool.community.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /귓속말 <닉네임> <메시지>} (aliases {@code /w}, {@code /msg}, {@code /tell})
 * — open to any online player, not restricted to friends. Remembers the last
 * sender per recipient so {@link ReplyCommand} (/답장) can reply without
 * retyping the name.
 */
public final class WhisperCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Map<UUID, UUID> lastWhisperFrom = new ConcurrentHashMap<>();

    public WhisperCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
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
            messages.send(player, "chat.whisper-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "chat.player-not-found");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "chat.cannot-whisper-self");
            return true;
        }
        sendWhisper(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    /** Also used by {@link ReplyCommand} so both paths share the same mute check and delivery. */
    public void sendWhisper(Player from, Player to, String message) {
        core.punishments().activeMute(from.getUniqueId()).thenAccept(mute ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (mute.isPresent()) {
                        messages.send(from, "chat.muted");
                        return;
                    }
                    messages.send(to, "chat.whisper-received",
                            Placeholder.unparsed("from", from.getName()), Placeholder.unparsed("message", message));
                    messages.send(from, "chat.whisper-sent",
                            Placeholder.unparsed("to", to.getName()), Placeholder.unparsed("message", message));
                    lastWhisperFrom.put(to.getUniqueId(), from.getUniqueId());
                }));
    }

    public UUID lastWhisperFrom(UUID recipient) {
        return lastWhisperFrom.get(recipient);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
