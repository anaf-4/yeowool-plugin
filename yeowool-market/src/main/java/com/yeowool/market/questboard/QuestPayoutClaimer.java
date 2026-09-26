package com.yeowool.market.questboard;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Pays out {@code yw_quest_payouts} rows to players online on THIS server.
 * A row is claimed by deleting it (only one server's DELETE can win), then
 * credited on the main thread if the player is still here; if they left in
 * between, the row is written back for whichever server they're on next.
 */
public final class QuestPayoutClaimer {

    private static final String SOURCE = "YeowoolMarket";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final QuestRepository repository;
    private final Executor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public QuestPayoutClaimer(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                              QuestRepository repository, Executor executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
    }

    /** Main thread. */
    public void claimAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            claim(player.getUniqueId());
        }
    }

    /** Main thread. No-op if the player isn't online here or a claim for them is already running. */
    public void claim(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null || !inFlight.add(uuid)) {
            return;
        }
        executor.execute(() -> {
            List<QuestRepository.Payout> claimed = new ArrayList<>();
            try {
                for (QuestRepository.Payout payout : repository.pendingPayouts(uuid)) {
                    if (repository.deletePayout(payout.id())) {
                        claimed.add(payout);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "의뢰 정산 장부 조회 실패 (" + uuid + ")", e);
            }
            runOnMain(() -> {
                inFlight.remove(uuid);
                credit(uuid, claimed);
            }, uuid, claimed);
        });
    }

    private void credit(UUID uuid, List<QuestRepository.Payout> claimed) {
        if (claimed.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        List<QuestRepository.Payout> unpaid = new ArrayList<>();
        long total = 0;
        for (QuestRepository.Payout payout : claimed) {
            if (player != null && core.economyData().modifyBalance(uuid, payout.amount(), SOURCE, payout.reason())) {
                total += payout.amount();
            } else {
                unpaid.add(payout);
            }
        }
        if (total > 0) {
            messages.send(player, "questboard.payout-received", Placeholder.unparsed("amount", String.format("%,d", total)));
        }
        if (!unpaid.isEmpty()) {
            executor.execute(() -> restore(unpaid));
        }
    }

    /** Puts claimed-but-unpaid rows back so the player gets them on their next server. */
    private void restore(List<QuestRepository.Payout> unpaid) {
        for (QuestRepository.Payout payout : unpaid) {
            try {
                repository.insertPayout(payout.player(), payout.amount(), payout.reason());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "의뢰 정산 복구 실패 — 수동 지급 필요: " + payout.player()
                        + " " + payout.amount() + "온 (" + payout.reason() + ")", e);
            }
        }
    }

    /** Schedules on the main thread; if the plugin is already disabled, restores the rows instead of losing them. */
    private void runOnMain(Runnable task, UUID uuid, List<QuestRepository.Payout> claimed) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            inFlight.remove(uuid);
            restore(claimed);
        }
    }
}
