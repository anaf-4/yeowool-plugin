package com.yeowool.core.mailbox;

import com.yeowool.core.api.service.MailboxService;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Tries to hand over any queued mailbox items the moment a player rejoins, and nudges them toward {@code /우편함} for whatever still didn't fit. */
public final class MailboxJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final MailboxService mailbox;
    private final MessageService messages;

    public MailboxJoinListener(JavaPlugin plugin, MailboxService mailbox, MessageService messages) {
        this.plugin = plugin;
        this.mailbox = mailbox;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        mailbox.tryDeliverAll(player).thenAccept(delivered -> {
            if (delivered > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        messages.send(player, "mailbox.delivered-on-join", Placeholder.unparsed("count", String.valueOf(delivered)));
                    }
                });
            }
            mailbox.loadPending(player.getUniqueId()).thenAccept(remaining -> {
                if (!remaining.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            messages.send(player, "mailbox.pending-notice", Placeholder.unparsed("count", String.valueOf(remaining.size())));
                        }
                    });
                }
            });
        });
    }
}
