package com.yeowool.life.competition;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Daily cross-server life competition. Scores are buffered per server and
 * added to the shared table every 10 seconds; once a window has ended (plus
 * a grace period for the last buffers) exactly one server claims the payout
 * and queues rewards through the core payout ledger, and every server
 * announces results it hasn't announced yet.
 */
public final class LifeCompetitionService {

    private record ScoreKey(String dateKey, UUID player) {
    }

    private static final long SETTLE_GRACE_MILLIS = 30_000;
    private static final String SOURCE = "YeowoolLife";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final CompetitionRepository repository;
    private final CompetitionSchedule schedule;
    private final Map<Integer, Long> rewardsByRank;
    private final Executor executor;

    // main thread only
    private Map<ScoreKey, Long> pending = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<String, CompetitionSchedule.Window> pendingWindows = new HashMap<>();
    private String announcedStart;

    private volatile long resultsAnnouncedUntil = System.currentTimeMillis();

    public LifeCompetitionService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, CompetitionRepository repository,
                                  CompetitionSchedule schedule, Map<Integer, Long> rewardsByRank, Executor executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.schedule = schedule;
        this.rewardsByRank = rewardsByRank;
        this.executor = executor;
    }

    /** Main thread: one completed action; counted only while that activity's competition is running. */
    public void record(Player player, CompetitionActivity activity) {
        Optional<CompetitionSchedule.Window> window = schedule.activeAt(ZonedDateTime.now());
        if (window.isEmpty() || window.get().activity() != activity) {
            return;
        }
        pending.merge(new ScoreKey(window.get().dateKey(), player.getUniqueId()), 1L, Long::sum);
        names.put(player.getUniqueId(), player.getName());
        pendingWindows.put(window.get().dateKey(), window.get());
    }

    /** Main thread, every 10 seconds: start announcement and handing buffered scores to the DB. */
    public void tick() {
        ZonedDateTime now = ZonedDateTime.now();
        schedule.activeAt(now).ifPresent(window -> {
            if (window.dateKey().equals(announcedStart)) {
                return;
            }
            announcedStart = window.dateKey();
            // Only announce right at the start — a server restarted mid-competition stays quiet.
            if (Duration.between(window.start(), now).toSeconds() < 60) {
                messages.broadcast("competition.started",
                        Placeholder.unparsed("activity", window.activity().label()),
                        Placeholder.unparsed("minutes", String.valueOf(Duration.between(window.start(), window.end()).toMinutes())),
                        Placeholder.unparsed("score_label", window.activity().scoreLabel()));
            }
        });
        flush();
    }

    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<ScoreKey, Long> batch = pending;
        pending = new HashMap<>();
        Map<UUID, String> batchNames = new HashMap<>(names);
        names.clear();
        List<CompetitionSchedule.Window> windows = new ArrayList<>(pendingWindows.values());
        pendingWindows.clear();
        List<CompetitionRepository.ScoreDelta> deltas = new ArrayList<>();
        batch.forEach((key, delta) -> deltas.add(new CompetitionRepository.ScoreDelta(
                key.dateKey(), key.player(), batchNames.getOrDefault(key.player(), "?"), delta)));
        executor.execute(() -> {
            try {
                for (CompetitionSchedule.Window window : windows) {
                    repository.ensureCompetition(window.dateKey(), window.activity().key(), window.end().toInstant().toEpochMilli());
                }
                repository.addScores(deltas);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "생활 대회 점수 저장 실패 — 이번 묶음 " + deltas.size() + "건 유실", e);
            }
        });
    }

    /** Worker thread, every minute on every server: pay out ended competitions once, then announce new results. */
    public void settleAndAnnounce() {
        long now = System.currentTimeMillis();
        try {
            for (CompetitionRepository.Competition competition : repository.unpaidEnded(now - SETTLE_GRACE_MILLIS)) {
                if (!repository.claimPayout(competition.dateKey(), now - SETTLE_GRACE_MILLIS, now)) {
                    continue;
                }
                String label = CompetitionActivity.byKey(competition.activity()).map(CompetitionActivity::label).orElse(competition.activity());
                int ranks = rewardsByRank.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                List<CompetitionRepository.Standing> top = repository.top(competition.dateKey(), ranks);
                // ponytail: the claim and these inserts aren't one transaction — a crash in between loses the rewards; each is logged to pay by hand.
                for (int i = 0; i < top.size(); i++) {
                    long reward = rewardsByRank.getOrDefault(i + 1, 0L);
                    CompetitionRepository.Standing standing = top.get(i);
                    String reason = "생활 대회(" + label + ") " + (i + 1) + "위";
                    try {
                        core.payouts().enqueue(standing.player(), reward, SOURCE, reason);
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "생활 대회 보상 장부 기록 실패 — 수동 지급 필요: "
                                + standing.player() + " " + reward + "온 (" + reason + ")", e);
                    }
                }
            }
            List<CompetitionRepository.Finished> finished = repository.paidSince(resultsAnnouncedUntil);
            if (finished.isEmpty()) {
                return;
            }
            resultsAnnouncedUntil = finished.get(finished.size() - 1).paidAt();
            Map<CompetitionRepository.Finished, List<CompetitionRepository.Standing>> results = new LinkedHashMap<>();
            for (CompetitionRepository.Finished competition : finished) {
                results.put(competition, repository.top(competition.dateKey(), 3));
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> results.forEach(this::announceResult));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "생활 대회 정산/공지 처리 실패", e);
        }
    }

    private void announceResult(CompetitionRepository.Finished competition, List<CompetitionRepository.Standing> top) {
        CompetitionActivity activity = CompetitionActivity.byKey(competition.activity()).orElse(CompetitionActivity.FISHING);
        if (top.isEmpty()) {
            messages.broadcast("competition.result-empty", Placeholder.unparsed("activity", activity.label()));
            return;
        }
        messages.broadcast("competition.result-header", Placeholder.unparsed("activity", activity.label()));
        for (int i = 0; i < top.size(); i++) {
            CompetitionRepository.Standing standing = top.get(i);
            messages.broadcast("competition.result-line",
                    Placeholder.unparsed("rank", String.valueOf(i + 1)),
                    Placeholder.unparsed("player", standing.name()),
                    Placeholder.unparsed("score", String.format("%,d", standing.score())),
                    Placeholder.unparsed("unit", activity.unit()));
        }
    }

    /** Main thread: {@code /생활대회}. */
    public void showStatus(Player player) {
        ZonedDateTime now = ZonedDateTime.now();
        Optional<CompetitionSchedule.Window> active = schedule.activeAt(now);
        if (active.isEmpty()) {
            CompetitionSchedule.Window next = schedule.nextAfter(now);
            messages.send(player, "competition.next",
                    Placeholder.unparsed("time", TIME.format(next.start())),
                    Placeholder.unparsed("activity", next.activity().label()),
                    Placeholder.unparsed("score_label", next.activity().scoreLabel()));
            return;
        }
        CompetitionSchedule.Window window = active.get();
        UUID uuid = player.getUniqueId();
        long unsaved = pending.getOrDefault(new ScoreKey(window.dateKey(), uuid), 0L);
        String remaining = DurationFormat.humanize(Duration.between(now, window.end()).toMillis());
        executor.execute(() -> {
            List<CompetitionRepository.Standing> top;
            long mine;
            try {
                top = repository.top(window.dateKey(), 5);
                mine = repository.scoreOf(window.dateKey(), uuid) + unsaved;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "생활 대회 순위 조회 실패", e);
                reply(uuid, p -> messages.send(p, "competition.error"));
                return;
            }
            reply(uuid, p -> {
                messages.send(p, "competition.status-header",
                        Placeholder.unparsed("activity", window.activity().label()),
                        Placeholder.unparsed("remaining", remaining));
                if (top.isEmpty()) {
                    messages.send(p, "competition.status-empty");
                }
                for (int i = 0; i < top.size(); i++) {
                    messages.send(p, "competition.status-line",
                            Placeholder.unparsed("rank", String.valueOf(i + 1)),
                            Placeholder.unparsed("player", top.get(i).name()),
                            Placeholder.unparsed("score", String.format("%,d", top.get(i).score())),
                            Placeholder.unparsed("unit", window.activity().unit()));
                }
                messages.send(p, "competition.status-mine",
                        Placeholder.unparsed("score", String.format("%,d", mine)),
                        Placeholder.unparsed("unit", window.activity().unit()),
                        Placeholder.unparsed("score_label", window.activity().scoreLabel()));
            });
        });
    }

    private void reply(UUID uuid, java.util.function.Consumer<Player> action) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                action.accept(player);
            }
        });
    }
}
