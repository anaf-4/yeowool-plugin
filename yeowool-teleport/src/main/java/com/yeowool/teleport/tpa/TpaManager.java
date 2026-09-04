package com.yeowool.teleport.tpa;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory only, like {@code FriendManager}'s pending friend requests —
 * a teleport request only ever makes sense while both players are online,
 * and naturally expires on its own.
 */
public final class TpaManager {

    private static final long REQUEST_TTL_MILLIS = 60_000L;

    public enum Kind { TO, HERE }

    public record PendingRequest(UUID requester, Kind kind) {
    }

    private record TimedRequest(UUID requester, Kind kind, long expiresAt) {
    }

    private final Map<UUID, TimedRequest> pendingByTarget = new ConcurrentHashMap<>();

    public void request(UUID requester, UUID target, Kind kind) {
        pendingByTarget.put(target, new TimedRequest(requester, kind, System.currentTimeMillis() + REQUEST_TTL_MILLIS));
    }

    public Optional<PendingRequest> pending(UUID target) {
        TimedRequest request = pendingByTarget.get(target);
        if (request == null || System.currentTimeMillis() > request.expiresAt()) {
            return Optional.empty();
        }
        return Optional.of(new PendingRequest(request.requester(), request.kind()));
    }

    public void clear(UUID target) {
        pendingByTarget.remove(target);
    }
}
