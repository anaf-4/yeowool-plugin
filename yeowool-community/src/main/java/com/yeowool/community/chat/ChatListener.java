package com.yeowool.community.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Section 9.1 (YeowoolChat): normal typed chat, routed through whichever
 * channel ({@link ChatChannelService.Channel}) the player currently has
 * selected via {@code /채널}. The event is always cancelled and delivery
 * handled manually through {@link ChatChannelService#send} so the exact
 * recipient set (everyone / nearby / land members) is under our control
 * rather than Paper's default "send to every online player" behavior.
 */
public final class ChatListener implements Listener {

    private final YeowoolCoreAPI core;
    private final ChatChannelService channelService;
    private final MessageService messages;
    private final long cooldownMillis;

    private final Map<java.util.UUID, Long> lastMessageAt = new ConcurrentHashMap<>();

    public ChatListener(YeowoolCoreAPI core, ChatChannelService channelService, MessageService messages, long cooldownMillis) {
        this.core = core;
        this.channelService = channelService;
        this.messages = messages;
        this.cooldownMillis = cooldownMillis;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        event.setCancelled(true);

        var mute = core.punishments().activeMute(player.getUniqueId()).join();
        if (mute.isPresent()) {
            var entry = mute.get();
            String expiry = entry.isPermanent() ? "영구" : "만료: " + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(entry.expiresAt()));
            player.sendMessage(Component.text("채팅이 음소거되었습니다. 사유: " + entry.reason() + " (" + expiry + ")", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastMessageAt.put(player.getUniqueId(), now);
        if (last != null && now - last < cooldownMillis) {
            messages.send(player, "chat.cooldown");
            return;
        }

        var channel = channelService.currentChannel(player);
        if (channel == ChatChannelService.Channel.LAND && !channelService.hasLand(player)) {
            messages.send(player, "chat.no-land");
            return;
        }

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        channelService.send(player, channel, plainMessage);
    }
}
