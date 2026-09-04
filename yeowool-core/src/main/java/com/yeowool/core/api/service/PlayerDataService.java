package com.yeowool.core.api.service;

import com.yeowool.core.api.model.PlayerData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the lifecycle of {@link PlayerData}: load on join, cache while online,
 * save on quit/interval/shutdown. Other Yeowool plugins should treat this as
 * the single source of truth for a player's core row instead of querying the
 * database directly.
 */
public interface PlayerDataService {

    /**
     * Returns the cached data for a player, if currently loaded (online, or
     * still warm in cache after a recent quit).
     */
    Optional<PlayerData> getIfLoaded(UUID uuid);

    /**
     * Returns the cached data for an online player, throwing if it is not
     * loaded. Convenience for call sites that only ever run for online
     * players (commands, listeners) where a missing entry is a bug.
     */
    PlayerData getOnline(UUID uuid);

    /**
     * Loads a player's data from the database into cache, creating a fresh
     * row if this is their first time. Safe to call even if already cached
     * (returns the cached instance).
     */
    CompletableFuture<PlayerData> load(UUID uuid, String username);

    /**
     * Persists the given player's data if it has pending changes.
     */
    CompletableFuture<Void> save(PlayerData data);

    /**
     * Persists every currently cached player's dirty data. Used on shutdown
     * and by the periodic autosave task.
     */
    CompletableFuture<Void> saveAll();

    /**
     * Removes a player from cache after it has been saved. Called on quit,
     * after a short grace period to tolerate quick relogs.
     */
    void unload(UUID uuid);
}
