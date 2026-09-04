package com.yeowool.core.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired synchronously whenever {@link com.yeowool.core.api.service.EconomyDataService}
 * changes a wallet or bank balance. Mainly meant for YeowoolAdmin's
 * anti-exploit monitoring to catch anomalously large grants live, without
 * polling the {@code yw_logs} table.
 */
public final class PlayerBalanceChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Account { WALLET, BANK, CASH }

    private final UUID uuid;
    private final Account account;
    private final long amount;
    private final long newBalance;
    private final String sourcePlugin;
    private final String reason;

    public PlayerBalanceChangeEvent(UUID uuid, Account account, long amount, long newBalance, String sourcePlugin, String reason) {
        this.uuid = uuid;
        this.account = account;
        this.amount = amount;
        this.newBalance = newBalance;
        this.sourcePlugin = sourcePlugin;
        this.reason = reason;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Account getAccount() {
        return account;
    }

    public long getAmount() {
        return amount;
    }

    public long getNewBalance() {
        return newBalance;
    }

    public String getSourcePlugin() {
        return sourcePlugin;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
