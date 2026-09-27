package com.yeowool.core.payout;

import com.yeowool.core.api.service.EconomyDataService;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.PayoutService;
import com.yeowool.core.data.repository.PayoutRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * {@link PayoutService} implementation. A pending row is claimed by deleting
 * it (only one server's DELETE wins), then credited on the main thread if the
 * player is still online here; otherwise the row is written back for their
 * next server. Claimed-but-unpaid batches are tracked so {@link #restoreUnpaid}
 * can put them back when the plugin shuts down mid-claim.
 */
public final class PayoutManager implements PayoutService, Listener {

    private final JavaPlugin plugin;
    private final PayoutRepository repository;
    private final EconomyDataService economy;
    private final MessageService messages;
    private final Executor executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<PayoutRepository.Payout>> claimedUnpaid = new ConcurrentHashMap<>();

    public PayoutManager(JavaPlugin plugin, PayoutRepository repository, EconomyDataService economy,
                         MessageService messages, Executor executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.messages = messages;
        this.executor = executor;
    }

    @Override
    public void enqueue(UUID player, long amount, String sourcePlugin, String reason) throws SQLException {
        if (amount <= 0) {
            return;
        }
        repository.insert(player, amount, sourcePlugin, reason);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> claimNow(uuid), 60L);
    }

    /** Main thread, every minute. */
    public void claimAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            claimNow(player.getUniqueId());
        }
    }

    @Override
    public void claimNow(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null || !inFlight.add(uuid)) {
            return;
        }
        executor.execute(() -> {
            List<PayoutRepository.Payout> claimed = new ArrayList<>();
            try {
                for (PayoutRepository.Payout payout : repository.pending(uuid)) {
                    if (repository.delete(payout.id())) {
                        claimed.add(payout);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "지급 장부 조회 실패 (" + uuid + ")", e);
            }
            if (!claimed.isEmpty()) {
                claimedUnpaid.put(uuid, claimed);
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.remove(uuid);
                    List<PayoutRepository.Payout> batch = claimedUnpaid.remove(uuid);
                    if (batch != null) {
                        credit(uuid, batch);
                    }
                });
            } else {
                inFlight.remove(uuid); // restoreUnpaid() in onDisable puts the batch back
            }
        });
    }

    private void credit(UUID uuid, List<PayoutRepository.Payout> batch) {
        Player player = Bukkit.getPlayer(uuid);
        List<PayoutRepository.Payout> unpaid = new ArrayList<>();
        for (PayoutRepository.Payout payout : batch) {
            boolean paid;
            try {
                paid = player != null && economy.modifyBalance(uuid, payout.amount(), payout.source(), payout.reason());
            } catch (RuntimeException e) { // e.g. player data not loaded (evicted from cache)
                plugin.getLogger().log(Level.WARNING, "지급 실패 — 장부로 되돌립니다 (" + uuid + ")", e);
                paid = false;
            }
            if (paid) {
                messages.send(player, "payout.received",
                        Placeholder.unparsed("reason", payout.reason()),
                        Placeholder.unparsed("amount", String.format("%,d", payout.amount())));
            } else {
                unpaid.add(payout);
            }
        }
        if (!unpaid.isEmpty()) {
            executor.execute(() -> restore(unpaid));
        }
    }

    /** Main thread, from onDisable after the executor drained: puts back every claimed-but-unpaid payout. */
    public void restoreUnpaid() {
        for (UUID uuid : List.copyOf(claimedUnpaid.keySet())) {
            List<PayoutRepository.Payout> batch = claimedUnpaid.remove(uuid);
            if (batch != null) {
                restore(batch);
            }
        }
    }

    private void restore(List<PayoutRepository.Payout> payouts) {
        for (PayoutRepository.Payout payout : payouts) {
            try {
                repository.insert(payout.player(), payout.amount(), payout.source(), payout.reason());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "지급 장부 복구 실패 — 수동 지급 필요: " + payout.player()
                        + " " + payout.amount() + "온 (" + payout.reason() + ")", e);
            }
        }
    }
}
