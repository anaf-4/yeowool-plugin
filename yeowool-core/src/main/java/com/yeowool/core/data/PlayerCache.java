package com.yeowool.core.data;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.config.CoreConfig;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around a Caffeine cache of online (and recently-online)
 * {@link PlayerData}. There is no explicit pinning of online players - every
 * {@link PlayerDataServiceImpl#getOnline}/{@link PlayerDataServiceImpl#load}
 * call refreshes an entry's {@code expireAfterAccess} timer, so a connected
 * player effectively never goes stale as long as *something* touches their
 * data at least once per {@code cache.player-cache-expire-minutes}. The one
 * real guarantee this does NOT provide is against {@code
 * cache.player-cache-max-size} (default 500) being set below the actual peak
 * concurrent player count: Caffeine's size-based eviction has no notion of
 * "online," so on an undersized config it could in principle evict a still-
 * connected player, and {@code getOnline()} would then throw for them.
 */
public final class PlayerCache {

    private final Cache<UUID, PlayerData> cache;

    public PlayerCache(CoreConfig config) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getCacheMaxSize())
                .expireAfterAccess(config.getCacheExpireMinutes(), TimeUnit.MINUTES)
                .build();
    }

    public Optional<PlayerData> get(UUID uuid) {
        return Optional.ofNullable(cache.getIfPresent(uuid));
    }

    public void put(PlayerData data) {
        cache.put(data.getUuid(), data);
    }

    public void invalidate(UUID uuid) {
        cache.invalidate(uuid);
    }

    public Iterable<PlayerData> all() {
        return cache.asMap().values();
    }
}
