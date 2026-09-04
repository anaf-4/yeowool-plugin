package com.yeowool.proxy.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Section 8.3 (YeowoolQueue): a simple per-server FIFO queue for when a
 * target backend is at its configured player cap, or entirely unreachable
 * (e.g. it's mid-restart). A repeating task (started by the main plugin
 * class) calls {@link #tick(RegisteredServer, int)} for every managed server
 * to try advancing each queue — it pings the server first, so a queue in
 * front of a server that's still down/restarting never gets shuffled to the
 * back by a failed connection attempt. Whenever someone ahead in line leaves
 * the queue — they got in, or they disconnected from the proxy entirely
 * while still waiting (see {@link #removePlayer}) — everyone behind them
 * gets a fresh "대기 순번" notice instead of only ever hearing their
 * position once, at the moment they joined.
 */
public final class QueueManager {

    private final ProxyServer proxy;
    private final Map<String, Map<UUID, Player>> queues = new ConcurrentHashMap<>();

    public QueueManager(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public boolean isQueued(Player player) {
        return queues.values().stream().anyMatch(q -> q.containsKey(player.getUniqueId()));
    }

    /** 이미 어딘가에 접속해있는 플레이어를 대기열에 등록하고 대기 전용 "queue" 서버로 옮긴다. */
    public void enqueue(Player player, RegisteredServer target) {
        register(player, target);
        moveToQueueServer(player);
    }

    /**
     * {@code PlayerChooseInitialServerEvent} 전용 — 아직 어느 백엔드에도 연결되지 않은
     * "최초 접속" 단계라, {@link #moveToQueueServer}로 새 연결을 만들 필요 없이 이벤트가
     * 직접 "queue"를 최초 접속 서버로 지정하면 된다.
     */
    public void enqueueForInitialConnect(Player player, RegisteredServer target) {
        register(player, target);
    }

    private void register(Player player, RegisteredServer target) {
        String name = target.getServerInfo().getName();
        // Plain LinkedHashMap, synchronized wrapper: enqueue (command thread), removePlayer
        // (DisconnectEvent on the Netty event-loop thread) and tick (scheduler thread) all touch
        // the same per-server queue concurrently - without this, two of them landing at once
        // could throw ConcurrentModificationException mid-tick or corrupt the map's internal
        // linked list.
        Map<UUID, Player> queue = queues.computeIfAbsent(name, key -> Collections.synchronizedMap(new LinkedHashMap<>()));
        int position;
        synchronized (queue) {
            queue.put(player.getUniqueId(), player);
            position = queue.size();
        }
        player.sendMessage(Component.text("대기열에 등록되었습니다. (대기 순번: "
                + position + ")", NamedTextColor.YELLOW));
    }

    /** 대기 중인 동안 머물 전용 서버("queue")로 보낸다 - 이미 그곳에 있으면 아무것도 안 함. */
    private void moveToQueueServer(Player player) {
        proxy.getServer("queue").ifPresent(queueServer -> {
            boolean alreadyOnQueue = player.getCurrentServer()
                    .map(conn -> conn.getServerInfo().getName().equals("queue"))
                    .orElse(false);
            if (!alreadyOnQueue) {
                player.createConnectionRequest(queueServer).connect();
            }
        });
    }

    /**
     * Removes a player from whichever queue they're waiting in — meant for
     * when they disconnect from the proxy entirely (not just the backend
     * they were queued for), since {@link #tick} alone would otherwise never
     * notice they're gone and everyone behind them would stay stuck one
     * position further back than necessary.
     */
    public void removePlayer(UUID uuid) {
        for (Map<UUID, Player> queue : queues.values()) {
            boolean removed;
            synchronized (queue) {
                removed = queue.remove(uuid) != null;
            }
            if (removed) {
                notifyAdvancedPositions(queue);
            }
        }
    }

    /**
     * {@code server}가 아예 응답하지 않으면(재시작/점검 중) 대기열 순서를 건드리지 않고
     * 다음 틱을 기다린다 — 먼저 접속을 시도했다가 실패해서 맨 뒤로 다시 등록되는 것을
     * 막기 위해, 연결을 시도하기 전에 항상 ping으로 살아있는지부터 확인한다.
     */
    public void tick(RegisteredServer server, int maxPlayers) {
        String name = server.getServerInfo().getName();
        Map<UUID, Player> queue = queues.get(name);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        server.ping().whenComplete((ping, error) -> {
            if (error != null) {
                return;
            }
            if (server.getPlayersConnected().size() >= maxPlayers) {
                return;
            }
            advanceQueue(server, queue);
        });
    }

    private void advanceQueue(RegisteredServer server, Map<UUID, Player> queue) {
        Player player;
        synchronized (queue) {
            var iterator = queue.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            var entry = iterator.next();
            player = proxy.getPlayer(entry.getKey()).orElse(null);
            iterator.remove();
        }
        // Whether this player successfully connects, fails and gets
        // re-enqueued at the back below, or vanished (proxy.getPlayer
        // returned empty), everyone still in the queue just moved up one —
        // tell them now rather than leaving them to wonder.
        notifyAdvancedPositions(queue);
        if (player == null) {
            return;
        }
        player.createConnectionRequest(server).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(Component.text("대기가 끝나 서버로 이동합니다.", NamedTextColor.GREEN));
            } else {
                enqueue(player, server);
            }
        });
    }

    /** Sends every currently-queued player (in order) their up-to-date position. */
    private void notifyAdvancedPositions(Map<UUID, Player> queue) {
        synchronized (queue) {
            int position = 1;
            for (Player queued : queue.values()) {
                queued.sendMessage(Component.text("대기 순번이 " + position + "번으로 당겨졌습니다.", NamedTextColor.YELLOW));
                position++;
            }
        }
    }
}
