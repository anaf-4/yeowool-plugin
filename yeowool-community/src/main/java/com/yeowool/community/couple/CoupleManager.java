package com.yeowool.community.couple;

import com.yeowool.community.couple.repository.CoupleRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Couples are exclusive 1:1 pairs, expected to stay small in count — fully
 * cached in memory (like {@code CouponManager}) so proposal validation
 * ("이미 커플이 있음") never blocks on a DB round trip. A pending proposal is
 * in-memory only, same TTL pattern as {@code FriendManager}'s requests.
 */
public final class CoupleManager {

    private static final long REQUEST_TTL_MILLIS = 60_000L;

    private record PendingRequest(UUID requester, long expiresAt) {
    }

    public enum ProposeResult { OK, ALREADY_SELF_PARTNERED, ALREADY_TARGET_PARTNERED, CANNOT_PROPOSE_SELF }

    private final JavaPlugin plugin;
    private final CoupleRepository repository;
    private final ExecutorService executor;
    private final Map<UUID, PendingRequest> pendingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();
    private final Map<UUID, Long> since = new ConcurrentHashMap<>();

    public CoupleManager(JavaPlugin plugin, CoupleRepository repository, ExecutorService executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    public void loadAll() throws SQLException {
        for (CoupleRepository.Entry entry : repository.loadAll()) {
            partners.put(entry.uuid(), entry.partner());
            since.put(entry.uuid(), entry.since());
        }
        plugin.getLogger().info("커플 " + (partners.size() / 2) + "쌍을 불러왔습니다.");
    }

    public boolean isPartnered(UUID uuid) {
        return partners.containsKey(uuid);
    }

    public Optional<UUID> partnerOf(UUID uuid) {
        return Optional.ofNullable(partners.get(uuid));
    }

    public Optional<Long> sinceOf(UUID uuid) {
        return Optional.ofNullable(since.get(uuid));
    }

    public ProposeResult propose(UUID requester, UUID target) {
        if (requester.equals(target)) {
            return ProposeResult.CANNOT_PROPOSE_SELF;
        }
        if (isPartnered(requester)) {
            return ProposeResult.ALREADY_SELF_PARTNERED;
        }
        if (isPartnered(target)) {
            return ProposeResult.ALREADY_TARGET_PARTNERED;
        }
        pendingByTarget.put(target, new PendingRequest(requester, System.currentTimeMillis() + REQUEST_TTL_MILLIS));
        return ProposeResult.OK;
    }

    public enum AcceptResult { OK, NO_PENDING_REQUEST, REQUESTER_NOW_PARTNERED, TARGET_NOW_PARTNERED }

    /** Accepts the pending proposal addressed to {@code target}. Returns the requester on success. */
    public AcceptResult accept(UUID target, java.util.function.Consumer<UUID> onSuccess) {
        PendingRequest pending = pendingByTarget.remove(target);
        if (pending == null || System.currentTimeMillis() > pending.expiresAt()) {
            return AcceptResult.NO_PENDING_REQUEST;
        }
        if (isPartnered(pending.requester())) {
            return AcceptResult.REQUESTER_NOW_PARTNERED;
        }
        if (isPartnered(target)) {
            return AcceptResult.TARGET_NOW_PARTNERED;
        }
        addCouple(pending.requester(), target);
        onSuccess.accept(pending.requester());
        return AcceptResult.OK;
    }

    public void decline(UUID target) {
        pendingByTarget.remove(target);
    }

    private void addCouple(UUID a, UUID b) {
        long now = System.currentTimeMillis();
        partners.put(a, b);
        partners.put(b, a);
        since.put(a, now);
        since.put(b, now);
        executor.execute(() -> {
            try {
                repository.insert(a, b, now);
            } catch (SQLException e) {
                plugin.getLogger().severe("커플 저장 실패: " + e.getMessage());
            }
        });
    }

    /** Returns the (now former) partner, if any. */
    public Optional<UUID> breakUp(UUID uuid) {
        UUID partner = partners.remove(uuid);
        if (partner == null) {
            return Optional.empty();
        }
        partners.remove(partner);
        since.remove(uuid);
        since.remove(partner);
        executor.execute(() -> {
            try {
                repository.delete(uuid, partner);
            } catch (SQLException e) {
                plugin.getLogger().severe("커플 해제 저장 실패: " + e.getMessage());
            }
        });
        return Optional.of(partner);
    }
}
