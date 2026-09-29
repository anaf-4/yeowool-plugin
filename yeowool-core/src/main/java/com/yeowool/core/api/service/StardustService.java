package com.yeowool.core.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 별조각 — a point currency stored only in the database, so any server can change any player's
 * balance atomically (online or not). Every method runs on core's DB worker and may be called from
 * any thread; failures are logged by core and complete the future exceptionally. A grant tells the
 * recipient in chat if they're online on this server.
 */
public interface StardustService {

    /** Adds {@code amount} (≤ 0 does nothing); completes with the amount granted. */
    CompletableFuture<Long> grant(UUID player, long amount, String sourcePlugin, String reason);

    /**
     * Like {@link #grant}, but at most {@code dailyCap} per {@code capKey} per player per server-local
     * day; completes with the amount actually granted (possibly 0).
     */
    CompletableFuture<Long> grantCapped(UUID player, long amount, String sourcePlugin, String reason, String capKey, long dailyCap);

    /** Takes {@code amount} only if the balance covers it; completes with whether it was taken. */
    CompletableFuture<Boolean> spend(UUID player, long amount, String sourcePlugin, String reason);

    CompletableFuture<Long> balance(UUID player);
}
