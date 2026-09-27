package com.yeowool.life.surprise;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

/**
 * Automatic surprise boost events shared by the participating servers (town and wild). Every
 * participating server polls the shared row; due transitions are claimed by conditional UPDATEs,
 * and each server applies the active boost locally, shows a boss bar and announces each new
 * {@code seq} once. Land XP / crop multipliers are left alone while a manual {@code /이벤트}
 * owns them.
 */
public final class SurpriseEventService {

    public record Settings(int durationMinutes, int intervalMinMinutes, int intervalMaxMinutes,
                           Map<SurpriseEventType, Double> multipliers) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final SurpriseEventRepository repository;
    private final Settings settings;
    private final Random random = new Random();

    // main thread only
    private SurpriseEventRepository.State current;
    private long announcedSeq = -1;
    private SurpriseEventType appliedType;
    private double appliedValue;
    private BossBar bossBar;

    public SurpriseEventService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                SurpriseEventRepository repository, Settings settings) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.settings = settings;
    }

    // ---- worker thread ----

    /** Every 20 seconds: advance the shared state if a transition is due, then apply the latest on the main thread. */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            SurpriseEventRepository.State state = repository.state();
            if (!state.active() && now >= state.nextAt()) {
                Optional<SurpriseEventType> type = SurpriseEventRules.pickType(random,
                        List.copyOf(settings.multipliers().keySet()), state.lastType());
                if (type.isPresent()) {
                    repository.start(state.seq(), type.get(), settings.multipliers().get(type.get()),
                            now + settings.durationMinutes() * 60_000L);
                    state = repository.state();
                }
            } else if (state.active() && now >= state.endsAt()) {
                repository.end(state.seq(), now + nextDelay());
                state = repository.state();
            }
            SurpriseEventRepository.State latest = state;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> apply(latest));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "깜짝 이벤트 상태 처리 실패", e);
        }
    }

    /** Staff start; {@code requested} empty = random. False if an event is already running. */
    public boolean forceStart(Optional<SurpriseEventType> requested) throws SQLException {
        SurpriseEventRepository.State state = repository.state();
        if (state.active()) {
            return false;
        }
        Optional<SurpriseEventType> type = requested.isPresent() ? requested
                : SurpriseEventRules.pickType(random, List.copyOf(settings.multipliers().keySet()), state.lastType());
        if (type.isEmpty()) {
            return false;
        }
        double multiplier = settings.multipliers().getOrDefault(type.get(), type.get() == SurpriseEventType.TREASURE_DROP ? 3.0 : 2.0);
        boolean started = repository.start(state.seq(), type.get(), multiplier,
                System.currentTimeMillis() + settings.durationMinutes() * 60_000L);
        if (started) {
            tick();
        }
        return started;
    }

    /** Staff end. False if nothing is running. */
    public boolean forceEnd() throws SQLException {
        SurpriseEventRepository.State state = repository.state();
        if (!state.active()) {
            return false;
        }
        boolean ended = repository.end(state.seq(), System.currentTimeMillis() + nextDelay());
        if (ended) {
            tick();
        }
        return ended;
    }

    private long nextDelay() {
        return SurpriseEventRules.nextDelayMillis(random, settings.intervalMinMinutes(), settings.intervalMaxMinutes());
    }

    // ---- main thread ----

    private void apply(SurpriseEventRepository.State state) {
        if (current != null && state.seq() < current.seq()) {
            return; // an older read finished late
        }
        current = state;
        SurpriseEventType wanted = state.active() ? state.type() : null;
        if (appliedType != wanted) {
            revert();
            if (wanted != null) {
                applyBoost(wanted, state.multiplier());
            }
        }
        if (announcedSeq < 0) {
            announcedSeq = state.seq(); // first read after startup isn't news
        } else if (state.seq() != announcedSeq) {
            announcedSeq = state.seq();
            announce(state);
        }
        updateBossBar();
    }

    /** Sets the boost; for shared landStats multipliers only when they're at 1.0 (a manual /이벤트 otherwise owns them — retried next tick). */
    private void applyBoost(SurpriseEventType type, double multiplier) {
        switch (type) {
            case LAND_XP -> {
                if (!SurpriseEventRules.stillOurs(core.landStats().getXpMultiplier(), 1.0)) {
                    return;
                }
                core.landStats().setXpMultiplier(multiplier);
            }
            case CROP_DROP -> {
                if (!SurpriseEventRules.stillOurs(core.landStats().getCropDropMultiplier(), 1.0)) {
                    return;
                }
                core.landStats().setCropDropMultiplier(multiplier);
            }
            case TREASURE_DROP -> LifeBoosts.setTreasureDropMultiplier(multiplier);
            case JOB_XP -> LifeBoosts.setJobXpMultiplier(multiplier);
        }
        appliedType = type;
        appliedValue = multiplier;
    }

    private void revert() {
        if (appliedType == null) {
            return;
        }
        switch (appliedType) {
            case LAND_XP -> {
                if (SurpriseEventRules.stillOurs(core.landStats().getXpMultiplier(), appliedValue)) {
                    core.landStats().setXpMultiplier(1.0);
                }
            }
            case CROP_DROP -> {
                if (SurpriseEventRules.stillOurs(core.landStats().getCropDropMultiplier(), appliedValue)) {
                    core.landStats().setCropDropMultiplier(1.0);
                }
            }
            case TREASURE_DROP -> LifeBoosts.setTreasureDropMultiplier(1.0);
            case JOB_XP -> LifeBoosts.setJobXpMultiplier(1.0);
        }
        appliedType = null;
    }

    private void announce(SurpriseEventRepository.State state) {
        if (state.type() == null) {
            return;
        }
        String multiplier = SurpriseEventRules.formatMultiplier(state.multiplier());
        if (state.active()) {
            long minutes = Math.max(1, (state.endsAt() - System.currentTimeMillis() + 59_999) / 60_000);
            messages.broadcast("surprise.started",
                    Placeholder.unparsed("type", state.type().label()),
                    Placeholder.unparsed("multiplier", multiplier),
                    Placeholder.unparsed("minutes", String.valueOf(minutes)));
        } else {
            messages.broadcast("surprise.ended",
                    Placeholder.unparsed("type", state.type().label()),
                    Placeholder.unparsed("multiplier", multiplier));
        }
    }

    /** Main thread, every second: boss bar while our boost is applied, removed otherwise. */
    public void updateBossBar() {
        if (current == null || !current.active() || appliedType == null) {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar = null;
            }
            return;
        }
        long remaining = Math.max(0, current.endsAt() - System.currentTimeMillis());
        String title = "🎉 깜짝 이벤트: " + appliedType.label() + " " + SurpriseEventRules.formatMultiplier(appliedValue)
                + "배 — 남은 시간 " + DurationFormat.humanize(remaining);
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(title, BarColor.PINK, BarStyle.SOLID);
        } else {
            bossBar.setTitle(title);
        }
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (settings.durationMinutes() * 60_000.0))));
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    public void status(CommandSender sender) {
        SurpriseEventRepository.State state = current;
        long now = System.currentTimeMillis();
        if (state != null && state.active() && state.type() != null) {
            messages.send(sender, "surprise.status-active",
                    Placeholder.unparsed("type", state.type().label()),
                    Placeholder.unparsed("multiplier", SurpriseEventRules.formatMultiplier(state.multiplier())),
                    Placeholder.unparsed("remaining", DurationFormat.humanize(Math.max(0, state.endsAt() - now))));
        } else if (state != null && state.nextAt() > now) {
            messages.send(sender, "surprise.status-idle",
                    Placeholder.unparsed("remaining", DurationFormat.humanize(state.nextAt() - now)));
        } else {
            messages.send(sender, "surprise.status-soon");
        }
    }

    /** Main thread, from onDisable: put every multiplier back and drop the boss bar. */
    public void shutdown() {
        revert();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
