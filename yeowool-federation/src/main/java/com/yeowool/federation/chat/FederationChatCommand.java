package com.yeowool.federation.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/** {@code /연합채팅 <메시지>} sends once; {@code /연합채팅} with no arguments toggles federation chat mode. */
public final class FederationChatCommand implements CommandExecutor {

    public static final String MODE_SETTING = "federation.chat-mode";
    public static final String MODE_ON = "on";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final FederationChatService chatService;
    private final ExecutorService executor;

    public FederationChatCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                 FederationChatService chatService, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.chatService = chatService;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "federation.player-only");
            return true;
        }
        if (args.length == 0) {
            toggleMode(player);
            return true;
        }
        String message = String.join(" ", args);
        executor.execute(() -> {
            try {
                sendResultFeedback(player, chatService.send(player, message));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 채팅 전송 실패", e);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 채팅 전송 실패", e);
            }
        });
        return true;
    }

    private void toggleMode(Player player) {
        var data = core.playerData().getIfLoaded(player.getUniqueId()).orElse(null);
        if (data == null) {
            return;
        }
        if (MODE_ON.equals(data.getSetting(MODE_SETTING, ""))) {
            data.setSetting(MODE_SETTING, "");
            messages.send(player, "federation.chat-mode-off");
            return;
        }
        executor.execute(() -> {
            try {
                boolean inFederation = chatService.findFederationId(player.getUniqueId()).isPresent();
                if (!inFederation) {
                    messages.send(player, "federation.chat-no-federation");
                    return;
                }
                data.setSetting(MODE_SETTING, MODE_ON);
                messages.send(player, "federation.chat-mode-on");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 채팅 모드 전환 실패", e);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 채팅 모드 전환 실패", e);
            }
        });
    }

    /** Safe to call from any thread — {@code Player#sendMessage} is thread-safe on Paper. */
    void sendResultFeedback(Player player, FederationChatService.SendResult result) {
        switch (result) {
            case SENT -> { }
            case MUTED -> messages.send(player, "federation.chat-muted",
                    Placeholder.unparsed("reason", chatService.activeMuteReason(player.getUniqueId()).orElse("")));
            case COOLDOWN -> messages.send(player, "federation.chat-cooldown");
            case NO_FEDERATION -> messages.send(player, "federation.chat-no-federation");
        }
    }
}
