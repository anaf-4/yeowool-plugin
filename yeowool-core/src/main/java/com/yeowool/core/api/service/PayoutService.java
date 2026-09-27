package com.yeowool.core.api.service;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Money owed to a player who may be offline or on another server. Rows go
 * into {@code yw_payouts}; whichever server the player is online on credits
 * them (shortly after join, every minute, or on {@link #claimNow}), so a
 * wallet is never written from a server that doesn't hold the player.
 */
public interface PayoutService {

    /** Records a payout. Blocking JDBC — call from a worker thread only. Amounts ≤ 0 are ignored. */
    void enqueue(UUID player, long amount, String sourcePlugin, String reason) throws SQLException;

    /** Main thread: pays out anything pending for {@code player} right away if they're online on this server. */
    void claimNow(UUID player);
}
