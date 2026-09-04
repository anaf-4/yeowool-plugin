package com.yeowool.life.fishing;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily fishing competition: starts automatically at {@code
 * fishing.competition.start-hour} server-local time (default 18:00), runs
 * for {@code fishing.competition.duration-minutes} (default 60), and ranks
 * whoever fished during the window by their single biggest catch — top 3
 * announced server-wide and (optionally) rewarded via {@code
 * fishing.competition.rewards}. Self-reschedules for the next day's start
 * the moment one competition ends, so {@link #scheduleNextStart} only ever
 * needs to be called once, from {@code onEnable}.
 *
 * <p>Everything here only ever runs on the main thread (event handlers and
 * Bukkit scheduler tasks both do), so a plain {@link HashMap} is safe —
 * no concurrent collection needed.
 */
public final class FishingCompetitionManager {

    /** One entrant's best catch so far this competition. */
    public record Entry(UUID playerId, String playerName, String speciesName, double sizeCm) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final int startHour;
    private final long durationTicks;
    private final Map<Integer, Long> rewardsByRank;

    private boolean active;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public FishingCompetitionManager(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                      int startHour, long durationMinutes, Map<Integer, Long> rewardsByRank) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.startHour = startHour;
        this.durationTicks = durationMinutes * 60L * 20L;
        this.rewardsByRank = rewardsByRank;
    }

    public boolean isActive() {
        return active;
    }

    public void scheduleNextStart() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextStart = now.toLocalDate().atTime(LocalTime.of(startHour, 0));
        if (!nextStart.isAfter(now)) {
            nextStart = nextStart.plusDays(1);
        }
        long delayTicks = Math.max(1, ChronoUnit.SECONDS.between(now, nextStart) * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::start, delayTicks);
    }

    private void start() {
        active = true;
        entries.clear();
        Bukkit.broadcast(Component.text("낚시 대회가 시작되었습니다! 1시간 동안 가장 큰 물고기를 낚아보세요.", NamedTextColor.AQUA, TextDecoration.BOLD));
        Bukkit.getScheduler().runTaskLater(plugin, this::end, durationTicks);
    }

    private void end() {
        active = false;
        List<Entry> top = entries.values().stream()
                .sorted(Comparator.comparingDouble(Entry::sizeCm).reversed())
                .limit(3)
                .toList();

        if (top.isEmpty()) {
            Bukkit.broadcast(Component.text("낚시 대회가 종료되었습니다. 참가자가 없었습니다.", NamedTextColor.GRAY));
        } else {
            Bukkit.broadcast(Component.text("낚시 대회가 종료되었습니다! 결과를 발표합니다.", NamedTextColor.AQUA, TextDecoration.BOLD));
            String[] medals = {"🥇 1위", "🥈 2위", "🥉 3위"};
            TextColor[] colors = {NamedTextColor.GOLD, NamedTextColor.GRAY, TextColor.color(0xCD7F32)};
            for (int i = 0; i < top.size(); i++) {
                Entry entry = top.get(i);
                Bukkit.broadcast(Component.text(medals[i] + " " + entry.playerName() + " - "
                        + entry.speciesName() + " (" + String.format("%.1f", entry.sizeCm()) + "cm)", colors[i]));
                long reward = rewardsByRank.getOrDefault(i + 1, 0L);
                if (reward > 0) {
                    core.economyData().modifyBalance(entry.playerId(), reward, "YeowoolLife", "낚시 대회 " + (i + 1) + "위 보상");
                    Player online = Bukkit.getPlayer(entry.playerId());
                    if (online != null) {
                        messages.send(online, "fishing.competition-reward",
                                Placeholder.unparsed("rank", String.valueOf(i + 1)),
                                Placeholder.unparsed("amount", String.format("%,d", reward)));
                    }
                }
            }
        }

        entries.clear();
        scheduleNextStart();
    }

    /** No-op if the competition isn't currently active, or {@code sizeCm} isn't a new personal best for this window. */
    public void recordCatch(Player player, String speciesName, double sizeCm) {
        if (!active) {
            return;
        }
        entries.merge(player.getUniqueId(), new Entry(player.getUniqueId(), player.getName(), speciesName, sizeCm),
                (existing, fresh) -> fresh.sizeCm() > existing.sizeCm() ? fresh : existing);
    }

    public List<Entry> currentTop(int limit) {
        return entries.values().stream()
                .sorted(Comparator.comparingDouble(Entry::sizeCm).reversed())
                .limit(limit)
                .toList();
    }
}
