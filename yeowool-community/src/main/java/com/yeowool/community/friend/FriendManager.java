package com.yeowool.community.friend;

import com.yeowool.community.friend.repository.FriendRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Friendships persist to {@code yw_friendships}; a pending request is
 * intentionally in-memory only (like {@code TradeManager}'s trade requests)
 * since it only ever makes sense while both players are online and expires
 * on its own after {@link #REQUEST_TTL_MILLIS}.
 */
public final class FriendManager {

    private static final long REQUEST_TTL_MILLIS = 60_000L;

    private record PendingRequest(UUID requester, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final FriendRepository repository;
    private final ExecutorService executor;
    private final Map<UUID, PendingRequest> pendingByTarget = new ConcurrentHashMap<>();

    public FriendManager(JavaPlugin plugin, FriendRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void sendRequest(UUID requester, UUID target) {
        pendingByTarget.put(target, new PendingRequest(requester, System.currentTimeMillis() + REQUEST_TTL_MILLIS));
    }

    /** Accepts the pending request addressed to {@code target}, if any and not expired. Returns the requester on success. */
    public Optional<UUID> accept(UUID target) {
        PendingRequest pending = pendingByTarget.remove(target);
        if (pending == null || System.currentTimeMillis() > pending.expiresAt()) {
            return Optional.empty();
        }
        addFriendship(pending.requester(), target);
        return Optional.of(pending.requester());
    }

    public void decline(UUID target) {
        pendingByTarget.remove(target);
    }

    private void addFriendship(UUID a, UUID b) {
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            try {
                repository.insert(a, b, now);
            } catch (SQLException e) {
                plugin.getLogger().severe("친구 추가 저장 실패: " + e.getMessage());
            }
        });
    }

    public void removeFriend(UUID a, UUID b) {
        executor.execute(() -> {
            try {
                repository.delete(a, b);
            } catch (SQLException e) {
                plugin.getLogger().severe("친구 삭제 저장 실패: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<UUID>> friendsOf(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.friendsOf(uuid);
            } catch (SQLException e) {
                plugin.getLogger().severe("친구 목록 조회 실패: " + e.getMessage());
                return List.<UUID>of();
            }
        }, executor);
    }

    public CompletableFuture<Boolean> areFriends(UUID a, UUID b) {
        return friendsOf(a).thenApply(friends -> friends.contains(b));
    }
}
