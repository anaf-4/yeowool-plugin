package com.yeowool.core.mailbox;

import com.yeowool.core.api.service.MailboxService;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** {@code /우편함} — opens {@link MailboxGui} with the caller's pending items. */
public final class MailboxCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MailboxService mailbox;
    private final MessageService messages;
    private final int backgroundOffsetPx;

    public MailboxCommand(JavaPlugin plugin, MailboxService mailbox, MessageService messages, int backgroundOffsetPx) {
        this.plugin = plugin;
        this.mailbox = mailbox;
        this.messages = messages;
        this.backgroundOffsetPx = backgroundOffsetPx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        mailbox.loadPending(player.getUniqueId()).thenAccept(entries ->
                Bukkit.getScheduler().runTask(plugin, () -> new MailboxGui(plugin, mailbox, messages, entries, 0, backgroundOffsetPx).open(player)));
        return true;
    }
}
