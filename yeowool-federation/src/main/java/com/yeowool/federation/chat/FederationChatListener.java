package com.yeowool.federation.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Federation chat mode: runs before yeowool-community's ChatListener (NORMAL,
 * ignoreCancelled), so cancelling here is enough to keep the message out of the
 * normal channels. Already on an async thread, so the JDBC lookups run inline.
 */
public final class FederationChatListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final FederationChatService chatService;
    private final FederationChatCommand command;

    public FederationChatListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                  FederationChatService chatService, FederationChatCommand command) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.chatService = chatService;
        this.command = command;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var data = core.playerData().getIfLoaded(player.getUniqueId()).orElse(null);
        if (data == null || !FederationChatCommand.MODE_ON.equals(data.getSetting(FederationChatCommand.MODE_SETTING, ""))) {
            return;
        }
        try {
            if (chatService.findFederationId(player.getUniqueId()).isEmpty()) {
                data.setSetting(FederationChatCommand.MODE_SETTING, "");
                messages.send(player, "federation.chat-mode-auto-off");
                return;
            }
            event.setCancelled(true);
            String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
            command.sendResultFeedback(player, chatService.send(player, plainMessage));
        } catch (SQLException e) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.SEVERE, "연합 채팅 모드 전송 실패", e);
        }
    }
}
