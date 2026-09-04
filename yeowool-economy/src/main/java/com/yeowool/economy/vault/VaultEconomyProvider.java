package com.yeowool.economy.vault;

import com.yeowool.core.api.YeowoolCoreAPI;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/**
 * Bridges Vault's {@link Economy} API onto "온" so third-party plugins
 * (EssentialsX, Citizens shop trait, other shop plugins, ...) can read and
 * charge the same wallet YeowoolEconomy's own commands use — those plugins
 * only ever talk to Vault, never to {@link YeowoolCoreAPI} directly. Only
 * registered if the server actually has Vault installed (see
 * {@link com.yeowool.economy.YeowoolEconomy}).
 *
 * <p>Vault's contract is synchronous, but our balances live in
 * YeowoolCore's cache which requires a player's data to be loaded first.
 * For online players that's already true; for offline players this blocks
 * the calling thread on a one-time DB load — acceptable since Vault calls
 * are typically infrequent commands, not a hot path, but worth knowing if
 * a caller invokes this rapidly for many offline players at once.
 *
 * <p>Vault's "bank" concept (a shared, multi-owner account) has no
 * equivalent here — it's a different idea from the personal bank balance
 * YeowoolEconomy exposes via {@code /은행} — so all bank-related methods
 * report "not supported" rather than pretending to implement it.
 */
public final class VaultEconomyProvider implements Economy {

    private final YeowoolCoreAPI core;

    public VaultEconomyProvider(YeowoolCoreAPI core) {
        this.core = core;
    }

    private UUID ensureLoaded(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        if (core.playerData().getIfLoaded(uuid).isEmpty()) {
            core.playerData().load(uuid, player.getName()).join();
        }
        return uuid;
    }

    private OfflinePlayer offline(String playerName) {
        return Bukkit.getOfflinePlayer(playerName);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "YeowoolEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return String.format("%,d온", Math.round(amount));
    }

    @Override
    public String currencyNamePlural() {
        return "온";
    }

    @Override
    public String currencyNameSingular() {
        return "온";
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(offline(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player.getUniqueId() != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(offline(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        UUID uuid = ensureLoaded(player);
        return core.economyData().getBalance(uuid);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(offline(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        UUID uuid = ensureLoaded(player);
        return core.economyData().hasBalance(uuid, Math.round(amount));
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(offline(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        UUID uuid = ensureLoaded(player);
        long rounded = Math.round(amount);
        boolean success = core.economyData().modifyBalance(uuid, -rounded, "Vault", "Vault 플러그인 출금");
        double balance = core.economyData().getBalance(uuid);
        return success
                ? new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, "")
                : new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "잔액 부족");
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(offline(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        UUID uuid = ensureLoaded(player);
        long rounded = Math.round(amount);
        core.economyData().modifyBalance(uuid, rounded, "Vault", "Vault 플러그인 입금");
        double balance = core.economyData().getBalance(uuid);
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 기능은 지원하지 않습니다.");
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notSupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(offline(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        ensureLoaded(player);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
}
