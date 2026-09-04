package com.yeowool.market.util;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;

import java.util.UUID;

/**
 * Shared 온/캐시 balance check + mutate, mirroring the currency switch
 * {@code ShopTransactions} uses for NPC shop trades so the player shop and
 * auction house can be currency-aware without duplicating that switch three
 * times over.
 */
public final class CurrencyOps {

    private CurrencyOps() {
    }

    public static boolean hasBalance(YeowoolCoreAPI core, UUID uuid, CurrencyType currency, long amount) {
        return currency == CurrencyType.CASH
                ? core.economyData().hasCashBalance(uuid, amount)
                : core.economyData().hasBalance(uuid, amount);
    }

    /** Adds {@code amount} (negative to subtract) to the player's balance in the given currency. */
    public static void modify(YeowoolCoreAPI core, UUID uuid, CurrencyType currency, long amount, String sourcePlugin, String reason) {
        if (currency == CurrencyType.CASH) {
            core.economyData().modifyCashBalance(uuid, amount, sourcePlugin, reason);
        } else {
            core.economyData().modifyBalance(uuid, amount, sourcePlugin, reason);
        }
    }
}
