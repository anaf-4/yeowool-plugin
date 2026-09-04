package com.yeowool.core.api.service;

import java.util.UUID;

/**
 * Raw storage for the "온" wallet and bank balance that live on the core
 * player row (see the plugin plan's Player struct). Business rules — transfer
 * fees, bank interest, shop pricing, minimum/maximum limits — belong in
 * YeowoolEconomy; this service only guarantees that every balance mutation is
 * atomic and unconditionally logged through {@link LogService}, since it is
 * the single choke point every economy-affecting plugin must call through.
 */
public interface EconomyDataService {

    long getBalance(UUID uuid);

    boolean hasBalance(UUID uuid, long amount);

    /**
     * Adds {@code amount} (use a negative value to subtract) to the player's
     * wallet and logs the change under {@code reason}. Returns false without
     * changing anything if the result would go negative.
     */
    boolean modifyBalance(UUID uuid, long amount, String sourcePlugin, String reason);

    long getBankBalance(UUID uuid);

    boolean hasBankBalance(UUID uuid, long amount);

    boolean modifyBankBalance(UUID uuid, long amount, String sourcePlugin, String reason);

    /**
     * "캐시" — the premium currency, kept as its own balance entirely
     * separate from 온 (see {@link com.yeowool.core.api.model.CurrencyType}).
     * Same atomicity/logging/event guarantees as the wallet/bank methods.
     */
    long getCashBalance(UUID uuid);

    boolean hasCashBalance(UUID uuid, long amount);

    boolean modifyCashBalance(UUID uuid, long amount, String sourcePlugin, String reason);
}
