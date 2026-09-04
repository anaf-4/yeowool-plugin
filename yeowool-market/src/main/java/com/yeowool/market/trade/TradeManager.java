package com.yeowool.market.trade;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending trade requests and active {@link TradeSession}s so a player
 * can't be dragged into two trades at once. A request expires after 30s if
 * not accepted.
 */
public final class TradeManager {

    private static final long REQUEST_TIMEOUT_TICKS = 20L * 30;

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>(); // target -> requester
    private final Map<UUID, TradeSession> activeSessions = new ConcurrentHashMap<>(); // playerUuid -> session

    public TradeManager(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    public boolean isBusy(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    public void sendRequest(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            messages.send(requester, "trade.cannot-trade-self");
            return;
        }
        if (isBusy(requester.getUniqueId()) || isBusy(target.getUniqueId())) {
            messages.send(requester, "trade.already-in-trade");
            return;
        }

        pendingRequests.put(target.getUniqueId(), requester.getUniqueId());
        messages.send(requester, "trade.request-sent", Placeholder.unparsed("target", target.getName()));
        messages.send(target, "trade.request-received", Placeholder.unparsed("requester", requester.getName()));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (requester.getUniqueId().equals(pendingRequests.get(target.getUniqueId()))) {
                pendingRequests.remove(target.getUniqueId());
                messages.send(requester, "trade.request-expired", Placeholder.unparsed("target", target.getName()));
                messages.send(target, "trade.request-expired", Placeholder.unparsed("target", requester.getName()));
            }
        }, REQUEST_TIMEOUT_TICKS);
    }

    public void accept(Player target) {
        UUID requesterUuid = pendingRequests.remove(target.getUniqueId());
        if (requesterUuid == null) {
            messages.send(target, "trade.no-pending-request");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester == null) {
            messages.send(target, "trade.requester-offline");
            return;
        }
        if (isBusy(requester.getUniqueId()) || isBusy(target.getUniqueId())) {
            messages.send(target, "trade.already-in-trade");
            return;
        }

        TradeSession session = new TradeSession(this, core, messages, requester.getUniqueId(), target.getUniqueId());
        activeSessions.put(requester.getUniqueId(), session);
        activeSessions.put(target.getUniqueId(), session);
        session.openFor(requester, target);
    }

    public void endSession(TradeSession session) {
        activeSessions.values().removeIf(s -> s == session);
    }

    /**
     * Force-cancels any session the given player is part of — called on
     * quit so a disconnect doesn't leave the other side's items in limbo.
     */
    public void forceCancel(UUID uuid) {
        TradeSession session = activeSessions.get(uuid);
        if (session != null) {
            session.cancel("trade.cancelled-disconnect");
        }
        pendingRequests.values().remove(uuid);
        pendingRequests.remove(uuid);
    }
}
