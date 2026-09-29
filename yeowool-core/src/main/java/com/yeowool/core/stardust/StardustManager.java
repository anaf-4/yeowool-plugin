package com.yeowool.core.stardust;

import com.yeowool.core.api.service.LogService;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.StardustService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

/** {@link StardustService} on {@code yw_stardust} / {@code yw_stardust_daily}; every change is one statement or one transaction. */
public final class StardustManager implements StardustService {

    @FunctionalInterface
    private interface SqlCall<T> {
        T call(Connection connection) throws SQLException;
    }

    private final JavaPlugin plugin;
    private final DataSource dataSource;
    private final Executor executor;
    private final LogService logs;
    private final MessageService messages;

    public StardustManager(JavaPlugin plugin, DataSource dataSource, Executor executor, LogService logs, MessageService messages) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.executor = executor;
        this.logs = logs;
        this.messages = messages;
    }

    @Override
    public CompletableFuture<Long> grant(UUID player, long amount, String sourcePlugin, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(0L);
        }
        return run("지급 " + player + " +" + amount + " (" + reason + ")", connection -> {
            addBalance(connection, player, amount);
            return amount;
        }).thenApply(granted -> afterGrant(player, granted, sourcePlugin, reason));
    }

    @Override
    public CompletableFuture<Long> grantCapped(UUID player, long amount, String sourcePlugin, String reason, String capKey, long dailyCap) {
        if (amount <= 0 || dailyCap <= 0) {
            return CompletableFuture.completedFuture(0L);
        }
        String day = LocalDate.now().toString();
        return run("한도 지급 " + player + " +" + amount + " (" + reason + ")", connection -> {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        // exclusive row lock up front (INSERT IGNORE would take a shared one and let two grants deadlock)
                        "INSERT INTO yw_stardust_daily (uuid, cap_key, day, amount) VALUES (?, ?, ?, 0) ON DUPLICATE KEY UPDATE amount = amount")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, capKey);
                    ps.setString(3, day);
                    ps.executeUpdate();
                }
                long used;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT amount FROM yw_stardust_daily WHERE uuid = ? AND cap_key = ? AND day = ? FOR UPDATE")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, capKey);
                    ps.setString(3, day);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        used = rs.getLong(1);
                    }
                }
                long granted = Math.max(0, Math.min(amount, dailyCap - used));
                if (granted > 0) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE yw_stardust_daily SET amount = amount + ? WHERE uuid = ? AND cap_key = ? AND day = ?")) {
                        ps.setLong(1, granted);
                        ps.setString(2, player.toString());
                        ps.setString(3, capKey);
                        ps.setString(4, day);
                        ps.executeUpdate();
                    }
                    addBalance(connection, player, granted);
                }
                connection.commit();
                return granted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // the pool resets it on return anyway
                }
            }
        }).thenApply(granted -> afterGrant(player, granted, sourcePlugin, reason));
    }

    @Override
    public CompletableFuture<Boolean> spend(UUID player, long amount, String sourcePlugin, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        return run("차감 " + player + " -" + amount + " (" + reason + ")", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE yw_stardust SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
                ps.setLong(1, amount);
                ps.setString(2, player.toString());
                ps.setLong(3, amount);
                return ps.executeUpdate() == 1;
            }
        }).thenApply(taken -> {
            if (taken) {
                log(sourcePlugin, player, reason, -amount);
            }
            return taken;
        });
    }

    @Override
    public CompletableFuture<Long> balance(UUID player) {
        return run("잔액 조회 " + player, connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM yw_stardust WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private static void addBalance(Connection connection, UUID player, long amount) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO yw_stardust (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = balance + VALUES(balance)")) {
            ps.setString(1, player.toString());
            ps.setLong(2, amount);
            ps.executeUpdate();
        }
    }

    private long afterGrant(UUID player, long granted, String sourcePlugin, String reason) {
        if (granted <= 0) {
            return granted;
        }
        log(sourcePlugin, player, reason, granted);
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player);
                if (online != null) {
                    messages.send(online, "stardust.received",
                            Placeholder.unparsed("amount", String.format("%,d", granted)),
                            Placeholder.unparsed("reason", reason));
                }
            });
        }
        return granted;
    }

    /** The balance change already committed — a logging failure must not fail the future. */
    private void log(String sourcePlugin, UUID player, String reason, long amount) {
        try {
            logs.log(sourcePlugin, "stardust", player, reason, Map.of("amount", String.valueOf(amount)));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "별조각 로그 기록 실패: " + player + " " + amount + " (" + reason + ")", e);
        }
    }

    private <T> CompletableFuture<T> run(String what, SqlCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    future.complete(call.call(connection));
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "별조각 처리 실패 — " + what, e);
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "별조각 처리 실패(종료 중) — " + what, e);
            future.completeExceptionally(e);
        }
        return future;
    }
}
