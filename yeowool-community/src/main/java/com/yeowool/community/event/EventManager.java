package com.yeowool.community.event;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

/**
 * Section 10.1 (YeowoolEvent) of the plugin plan, scoped to a server-wide
 * timed XP/작물 드랍 multiplier event with an announcement, a boss bar
 * countdown, and a once-per-event claimable reward (see {@code EventCommand})
 * — a concrete, buildable slice of "시즌 이벤트 / 이벤트 보상" rather than a
 * full events framework with NPCs and per-event custom loot tables.
 */
public final class EventManager {

    public enum Type {
        XP("경험치"), CROP_DROP("작물 드랍");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ActiveEvent(String name, Type type, double multiplier, long endAtMillis) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private volatile ActiveEvent active;
    private BukkitTask endTask;
    private BukkitTask barTask;
    private BossBar bossBar;

    public EventManager(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    public Optional<ActiveEvent> active() {
        return Optional.ofNullable(active);
    }

    public boolean isActive() {
        return active != null;
    }

    public void start(String name, Type type, double multiplier, int minutes) {
        stop(false);

        long startAt = System.currentTimeMillis();
        long durationMillis = minutes * 60_000L;
        active = new ActiveEvent(name, type, multiplier, startAt + durationMillis);
        applyMultiplier(type, multiplier);

        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("🎉 이벤트 시작: " + name + "\n", NamedTextColor.GOLD))
                .append(Component.text(type.label() + " " + multiplier + "배! (" + minutes + "분간) /이벤트 보상받기 로 참가 보상을 받으세요.\n", NamedTextColor.YELLOW))
                .append(Component.text("━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)));

        bossBar = Bukkit.createBossBar(bossBarTitle(name, type, multiplier, durationMillis), BarColor.YELLOW, BarStyle.SOLID);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        barTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active == null || bossBar == null) {
                return;
            }
            long remaining = active.endAtMillis() - System.currentTimeMillis();
            bossBar.setTitle(bossBarTitle(active.name(), active.type(), active.multiplier(), remaining));
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) durationMillis)));
            for (var player : Bukkit.getOnlinePlayers()) {
                bossBar.addPlayer(player);
            }
        }, 0L, 20L);

        endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> stop(true), minutes * 60L * 20L);
    }

    public void stop(boolean announce) {
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (barTask != null) {
            barTask.cancel();
            barTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (active == null) {
            return;
        }
        String name = active.name();
        active = null;
        core.landStats().setXpMultiplier(1.0);
        core.landStats().setCropDropMultiplier(1.0);

        if (announce) {
            Bukkit.broadcast(Component.text("이벤트 '" + name + "'가 종료되었습니다.", NamedTextColor.GRAY));
        }
    }

    private void applyMultiplier(Type type, double multiplier) {
        switch (type) {
            case XP -> core.landStats().setXpMultiplier(multiplier);
            case CROP_DROP -> core.landStats().setCropDropMultiplier(multiplier);
        }
    }

    private String bossBarTitle(String name, Type type, double multiplier, long remainingMillis) {
        return "🎉 " + name + " — " + type.label() + " " + multiplier + "배 (남은 시간: "
                + DurationFormat.humanize(Math.max(0, remainingMillis)) + ")";
    }
}
