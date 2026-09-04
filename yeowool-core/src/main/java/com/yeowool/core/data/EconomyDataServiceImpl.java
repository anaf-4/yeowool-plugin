package com.yeowool.core.data;

import com.yeowool.core.api.event.PlayerBalanceChangeEvent;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.EconomyDataService;
import com.yeowool.core.api.service.LogService;
import com.yeowool.core.api.service.PlayerDataService;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Mutates the cached {@link PlayerData} in memory only — persistence happens
 * on the normal save cycle (quit / autosave / shutdown), same as every other
 * field on the player row. Callers must ensure the target's data is already
 * loaded (e.g. via {@link PlayerDataService#load}); this keeps balance
 * changes synchronous and race-free instead of juggling async DB writes per
 * transaction.
 */
public final class EconomyDataServiceImpl implements EconomyDataService {

    private final PlayerDataService playerDataService;
    private final LogService logService;

    public EconomyDataServiceImpl(PlayerDataService playerDataService, LogService logService) {
        this.playerDataService = playerDataService;
        this.logService = logService;
    }

    private PlayerData require(UUID uuid) {
        return playerDataService.getIfLoaded(uuid).orElseThrow(() ->
                new NoSuchElementException("PlayerData for " + uuid + " is not loaded; call playerData().load() first"));
    }

    @Override
    public long getBalance(UUID uuid) {
        return require(uuid).getOnBalance();
    }

    @Override
    public boolean hasBalance(UUID uuid, long amount) {
        return require(uuid).getOnBalance() >= amount;
    }

    @Override
    public synchronized boolean modifyBalance(UUID uuid, long amount, String sourcePlugin, String reason) {
        PlayerData data = require(uuid);
        if (amount < 0 && data.getOnBalance() + amount < 0) {
            return false;
        }
        long newBalance = data.addOnBalance(amount);
        logService.log(sourcePlugin, "economy.wallet", uuid, reason, Map.of(
                "amount", String.valueOf(amount),
                "newBalance", String.valueOf(newBalance)
        ));
        Bukkit.getPluginManager().callEvent(new PlayerBalanceChangeEvent(
                uuid, PlayerBalanceChangeEvent.Account.WALLET, amount, newBalance, sourcePlugin, reason));
        return true;
    }

    @Override
    public long getBankBalance(UUID uuid) {
        return require(uuid).getBankBalance();
    }

    @Override
    public boolean hasBankBalance(UUID uuid, long amount) {
        return require(uuid).getBankBalance() >= amount;
    }

    @Override
    public synchronized boolean modifyBankBalance(UUID uuid, long amount, String sourcePlugin, String reason) {
        PlayerData data = require(uuid);
        if (amount < 0 && data.getBankBalance() + amount < 0) {
            return false;
        }
        long newBalance = data.addBankBalance(amount);
        logService.log(sourcePlugin, "economy.bank", uuid, reason, Map.of(
                "amount", String.valueOf(amount),
                "newBalance", String.valueOf(newBalance)
        ));
        Bukkit.getPluginManager().callEvent(new PlayerBalanceChangeEvent(
                uuid, PlayerBalanceChangeEvent.Account.BANK, amount, newBalance, sourcePlugin, reason));
        return true;
    }

    @Override
    public long getCashBalance(UUID uuid) {
        return require(uuid).getCashBalance();
    }

    @Override
    public boolean hasCashBalance(UUID uuid, long amount) {
        return require(uuid).getCashBalance() >= amount;
    }

    @Override
    public synchronized boolean modifyCashBalance(UUID uuid, long amount, String sourcePlugin, String reason) {
        PlayerData data = require(uuid);
        if (amount < 0 && data.getCashBalance() + amount < 0) {
            return false;
        }
        long newBalance = data.addCashBalance(amount);
        logService.log(sourcePlugin, "economy.cash", uuid, reason, Map.of(
                "amount", String.valueOf(amount),
                "newBalance", String.valueOf(newBalance)
        ));
        Bukkit.getPluginManager().callEvent(new PlayerBalanceChangeEvent(
                uuid, PlayerBalanceChangeEvent.Account.CASH, amount, newBalance, sourcePlugin, reason));
        return true;
    }
}
