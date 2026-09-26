package com.yeowool.federation.event;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.federation.FederationManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 연합대항. All state is in the DB so the 3 servers agree; every method except {@link #broadcast} is
 * blocking and runs on the federation executor. Each server remembers (in memory) which event it already
 * announced, so every server announces once to its own players.
 */
public final class FederationEventService {

    private static final int STATUS_TOP = 5;

    public enum StartResult { STARTED, ALREADY_RUNNING }

    public record EventStatus(long remainingMillis, List<EventStanding> top) {
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final FederationEventRepository events;
    private final FederationManager manager;
    private final List<Long> rewards;
    private final Optional<FederationEventSchedule> schedule;

    private volatile long announcedStartId = -1;
    private volatile long announcedEndId = -1;

    public FederationEventService(JavaPlugin plugin, MessageService messages, FederationEventRepository events,
                                  FederationManager manager, List<Long> rewards, Optional<FederationEventSchedule> schedule) {
        this.plugin = plugin;
        this.messages = messages;
        this.events = events;
        this.manager = manager;
        this.rewards = List.copyOf(rewards);
        this.schedule = schedule;
    }

    /** Don't re-announce whatever already happened before this server (re)started. */
    public void init() throws SQLException {
        Optional<FederationEvent> latest = events.findLatest();
        if (latest.isPresent()) {
            announcedStartId = latest.get().id();
            if (latest.get().result() != null) {
                announcedEndId = latest.get().id();
            }
        }
    }

    public StartResult start(int minutes) throws SQLException {
        if (events.findActive().isPresent()) {
            return StartResult.ALREADY_RUNNING;
        }
        long now = System.currentTimeMillis();
        events.startEvent(now, now + minutes * 60_000L, null);
        return StartResult.STARTED;
    }

    public boolean endNow() throws SQLException {
        Optional<FederationEvent> active = events.findActive();
        if (active.isEmpty()) {
            return false;
        }
        finish(active.get());
        return true;
    }

    public List<Announcement> tick(ZonedDateTime now) throws SQLException {
        long nowMillis = now.toInstant().toEpochMilli();
        if (schedule.isPresent()) {
            Optional<FederationEventSchedule.Window> window = schedule.get().activeWindow(now);
            if (window.isPresent() && events.findActive().isEmpty()) {
                events.startEvent(window.get().startMillis(), window.get().endMillis(), window.get().weekKey());
            }
        }

        Optional<FederationEvent> active = events.findActive();
        if (active.isPresent() && active.get().endsAt() <= nowMillis) {
            finish(active.get());
        }

        List<Announcement> announcements = new ArrayList<>();
        Optional<FederationEvent> latest = events.findLatest();
        if (latest.isPresent()) {
            FederationEvent event = latest.get();
            if (!event.ended() && event.id() != announcedStartId) {
                announcedStartId = event.id();
                long minutesLeft = Math.max(1, (event.endsAt() - nowMillis + 59_999) / 60_000);
                announcements.add(new Announcement("federation.event-started", "minutes", String.valueOf(minutesLeft)));
            }
            if (event.ended() && event.result() != null && event.id() != announcedEndId) {
                announcedEndId = event.id();
                announcedStartId = event.id();
                announcements.add(new Announcement("federation.event-ended", "results", event.result()));
            }
        }
        return announcements;
    }

    public Optional<EventStatus> status() throws SQLException {
        Optional<FederationEvent> active = events.findActive();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        long remaining = Math.max(0, active.get().endsAt() - System.currentTimeMillis());
        return Optional.of(new EventStatus(remaining, events.standings(active.get().id(), STATUS_TOP)));
    }

    /** Main thread only. */
    public void broadcast(List<Announcement> announcements) {
        for (Announcement announcement : announcements) {
            messages.broadcast(announcement.messageKey(), Placeholder.unparsed(announcement.placeholder(), announcement.value()));
        }
    }

    /** Only the server whose claim succeeds pays and stores the result. */
    private void finish(FederationEvent event) throws SQLException {
        if (!events.claimEnd(event.id(), System.currentTimeMillis())) {
            return;
        }
        List<EventStanding> top = events.standings(event.id(), rewards.size());
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            EventStanding standing = top.get(i);
            long reward = rewards.get(i);
            try {
                manager.deposit(standing.federationId(), reward);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합대항 보상 지급 실패 — 수동 지급 필요: " + standing.name() + " / " + reward + "온", e);
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(i + 1).append(". ").append(standing.name())
                    .append(" (+").append(String.format("%,d", standing.gained())).append(") — 보상 ")
                    .append(String.format("%,d", reward)).append("온");
        }
        events.saveResult(event.id(), result.length() == 0 ? "참가한 연합이 없습니다." : result.toString());
    }
}
