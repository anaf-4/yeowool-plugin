package com.yeowool.community.chat;

import com.yeowool.community.cosmetic.CosmeticDefinition;
import com.yeowool.community.cosmetic.CosmeticManager;
import com.yeowool.community.display.PlayerIdentityService;
import com.yeowool.core.api.YeowoolCoreAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared formatting + recipient-resolution for every chat channel (전체/
 * 지역/마을), used both by the normal chat pipeline ({@link ChatListener})
 * and the one-off {@code /전체}, {@code /지역}, {@code /마을} commands, so
 * both paths render and scope messages identically. "마을" is deliberately
 * mapped onto YeowoolLand's existing land-membership data (via
 * {@code core.landStats().getLandIds}) rather than a separate town/guild
 * system, since one doesn't exist in this plugin suite.
 */
public final class ChatChannelService {

    public enum Channel {
        GLOBAL, LOCAL, LAND;

        /** 전체/지역/마을 — used by the sidebar scoreboard's {@code <channel>} token and elsewhere a player-facing label is needed. */
        public String displayName() {
            return switch (this) {
                case GLOBAL -> "전체";
                case LOCAL -> "지역";
                case LAND -> "마을";
            };
        }
    }

    private static final String CHANNEL_SETTING_KEY = "chat.channel";

    private final YeowoolCoreAPI core;
    private final CosmeticManager cosmeticManager;
    private final PlayerIdentityService identityService;
    private final int localRadius;
    private final CrossServerChatBridge crossServerBridge;

    public ChatChannelService(YeowoolCoreAPI core, CosmeticManager cosmeticManager,
                               PlayerIdentityService identityService, int localRadius,
                               CrossServerChatBridge crossServerBridge) {
        this.core = core;
        this.cosmeticManager = cosmeticManager;
        this.identityService = identityService;
        this.localRadius = localRadius;
        this.crossServerBridge = crossServerBridge;
    }

    public Channel currentChannel(Player player) {
        var data = core.playerData().getOnline(player.getUniqueId());
        try {
            return Channel.valueOf(data.getSetting(CHANNEL_SETTING_KEY, Channel.GLOBAL.name()));
        } catch (IllegalArgumentException e) {
            return Channel.GLOBAL;
        }
    }

    public void setChannel(Player player, Channel channel) {
        core.playerData().getOnline(player.getUniqueId()).setSetting(CHANNEL_SETTING_KEY, channel.name());
    }

    public boolean hasLand(Player player) {
        return !core.landStats().getLandIds(player.getUniqueId()).isEmpty();
    }

    /** Formats and delivers one chat message on the given channel to exactly the players (+ console) who should see it. */
    public void send(Player sender, Channel channel, String plainMessage) {
        var data = core.playerData().getIfLoaded(sender.getUniqueId());
        TextColor nameColor = data.flatMap(d -> cosmeticManager.equipped(d, CosmeticDefinition.Type.CHAT_COLOR))
                .map(CosmeticDefinition::value)
                .map(this::parseColor)
                .orElse(NamedTextColor.WHITE);

        Component channelTag = switch (channel) {
            case LOCAL -> Component.text("[지역] ", NamedTextColor.AQUA);
            case LAND -> Component.text("[마을] ", NamedTextColor.GREEN);
            case GLOBAL -> Component.empty();
        };

        Component message = Component.text()
                .append(channelTag)
                .append(identityService.nameFor(sender, nameColor))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(plainMessage, NamedTextColor.WHITE))
                .build();

        for (Player recipient : resolveRecipients(sender, channel)) {
            recipient.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);

        if (channel == Channel.GLOBAL) {
            crossServerBridge.relay(sender, message);
        }
    }

    /**
     * {@code Collection<? extends Player>} (rather than {@code List<Player>})
     * so the GLOBAL branch can return {@code Bukkit.getOnlinePlayers()}
     * directly instead of copying it into a new list on every single
     * message — the LAND/LOCAL branches still need their own filtered list,
     * but the (by far the most common) GLOBAL case now allocates nothing.
     */
    private Collection<? extends Player> resolveRecipients(Player sender, Channel channel) {
        return switch (channel) {
            case GLOBAL -> Bukkit.getOnlinePlayers();
            case LOCAL -> sender.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(sender.getLocation()) <= (double) localRadius * localRadius)
                    .toList();
            case LAND -> {
                Set<UUID> senderLandIds = core.landStats().getLandIds(sender.getUniqueId());
                if (senderLandIds.isEmpty()) {
                    yield List.of(sender);
                }
                yield Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !Collections.disjoint(senderLandIds, core.landStats().getLandIds(p.getUniqueId())))
                        .toList();
            }
        };
    }

    private TextColor parseColor(String value) {
        TextColor named = NamedTextColor.NAMES.value(value.toLowerCase());
        if (named != null) {
            return named;
        }
        TextColor hex = TextColor.fromHexString(value);
        return hex != null ? hex : NamedTextColor.WHITE;
    }
}
