package com.yeowool.teleport.util;

import com.yeowool.teleport.playerwarp.PlayerWarpContext;

import java.util.UUID;

/** Thin wrapper over {@code core.economyData()} (온 currency only — player-warp fees never use 캐시) for the fee/admission flows scattered across the playerwarp GUIs. */
public final class PlayerWarpCurrency {

    private PlayerWarpCurrency() {
    }

    public static boolean has(PlayerWarpContext ctx, UUID uuid, long amount) {
        return amount <= 0 || ctx.core().economyData().hasBalance(uuid, amount);
    }

    public static void charge(PlayerWarpContext ctx, UUID uuid, long amount, String reason) {
        if (amount > 0) {
            ctx.core().economyData().modifyBalance(uuid, -amount, "YeowoolTeleport", reason);
        }
    }

    public static void grant(PlayerWarpContext ctx, UUID uuid, long amount, String reason) {
        if (amount > 0) {
            ctx.core().economyData().modifyBalance(uuid, amount, "YeowoolTeleport", reason);
        }
    }

    /** Buyer pays, seller (warp owner) receives — used for admission fees. */
    public static void pay(PlayerWarpContext ctx, UUID from, UUID to, long amount, String reason) {
        charge(ctx, from, amount, reason);
        grant(ctx, to, amount, reason);
    }
}
