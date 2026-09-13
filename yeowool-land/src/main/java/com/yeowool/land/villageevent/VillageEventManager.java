package com.yeowool.land.villageevent;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.land.LandManager;
import com.yeowool.land.command.LandCommand;
import com.yeowool.land.model.Land;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /마을대항} — for a set window, ranks every village by how much land
 * XP its owner gains (the same "village strength" metric {@code /마을랭킹}
 * already uses), then rewards the winner's village bank. Mirrors {@code
 * SeasonScoreListener}'s approach of reusing the existing land-XP signal
 * into a separate, resettable counter instead of touching real land XP —
 * here the counter lives only in memory for the duration of one event and
 * is discarded afterward (no DB table needed).
 */
public final class VillageEventManager {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final LandManager landManager;
    private final MessageService messages;
    private final long rewardOn;

    private final Map<UUID, Long> gained = new ConcurrentHashMap<>();
    private volatile boolean active = false;
    private volatile long endAtMillis;
    private BukkitTask autoEndTask;

    public VillageEventManager(JavaPlugin plugin, YeowoolCoreAPI core, LandManager landManager, MessageService messages, long rewardOn) {
        this.plugin = plugin;
        this.core = core;
        this.landManager = landManager;
        this.messages = messages;
        this.rewardOn = rewardOn;
    }

    public boolean isActive() {
        return active;
    }

    public long remainingMillis() {
        return active ? Math.max(0, endAtMillis - System.currentTimeMillis()) : 0;
    }

    /** {@code false} if an event is already running (caller should tell the sender to end it first). */
    public boolean start(int minutes) {
        if (active) {
            return false;
        }
        gained.clear();
        active = true;
        endAtMillis = System.currentTimeMillis() + minutes * 60_000L;
        autoEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> end(true), minutes * 60L * 20L);
        messages.broadcast("village-event.started", Placeholder.unparsed("minutes", String.valueOf(minutes)));
        return true;
    }

    /** Only counts owners — matches {@code /마을랭킹}'s definition of a village's strength as its owner's stat. */
    public void accumulate(UUID ownerCandidate, long delta) {
        if (!active || delta <= 0 || landManager.getLandOwnedBy(ownerCandidate).isEmpty()) {
            return;
        }
        gained.merge(ownerCandidate, delta, Long::sum);
    }

    public List<Map.Entry<UUID, Long>> standings(int topN) {
        return gained.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(topN)
                .toList();
    }

    /** {@code false} if no event was running (caller should tell the sender there's nothing to end). */
    public boolean endEarly() {
        if (!active) {
            return false;
        }
        end(false);
        return true;
    }

    private void end(boolean auto) {
        active = false;
        if (autoEndTask != null) {
            autoEndTask.cancel();
            autoEndTask = null;
        }

        var winner = gained.entrySet().stream().max(Map.Entry.comparingByValue());
        if (winner.isEmpty()) {
            messages.broadcast("village-event.ended-empty");
            gained.clear();
            return;
        }

        UUID ownerUuid = winner.get().getKey();
        long xpGained = winner.get().getValue();
        landManager.getLandOwnedBy(ownerUuid).ifPresentOrElse(land -> {
            long newBalance = landManager.modifyBankBalance(land, rewardOn);
            String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
            messages.broadcast("village-event.ended-winner",
                    Placeholder.unparsed("village", LandCommand.displayName(land)),
                    Placeholder.unparsed("owner", ownerName != null ? ownerName : "알 수 없음"),
                    Placeholder.unparsed("xp", String.format("%,d", xpGained)),
                    Placeholder.unparsed("reward", String.format("%,d", rewardOn)));
            core.logs().log("YeowoolLand", "village-event.reward", ownerUuid,
                    "마을대항 우승 보상: " + rewardOn + "온 (마을 은행 잔액 " + newBalance + ")", Map.of());
        }, () -> messages.broadcast("village-event.ended-empty"));

        gained.clear();
    }
}
