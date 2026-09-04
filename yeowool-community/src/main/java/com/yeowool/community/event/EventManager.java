package com.yeowool.community.event;

import com.yeowool.core.api.YeowoolCoreAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

/**
 * Section 10.1 (YeowoolEvent) of the plugin plan, scoped to a server-wide
 * timed XP-multiplier event with an announcement and a once-per-event
 * claimable reward (see {@code EventCommand}) — a concrete, buildable slice
 * of "시즌 이벤트 / 이벤트 보상" rather than a full events framework with
 * NPCs and per-event custom loot tables.
 */
public final class EventManager {

    public record ActiveEvent(String name, double multiplier, long endAtMillis) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private volatile ActiveEvent active;
    private BukkitTask endTask;

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

    public void start(String name, double multiplier, int minutes) {
        stop(false);

        long endAt = System.currentTimeMillis() + minutes * 60_000L;
        active = new ActiveEvent(name, multiplier, endAt);
        core.landStats().setXpMultiplier(multiplier);

        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("🎉 이벤트 시작: " + name + "\n", NamedTextColor.GOLD))
                .append(Component.text("경험치 " + multiplier + "배! (" + minutes + "분간) /이벤트 보상받기 로 참가 보상을 받으세요.\n", NamedTextColor.YELLOW))
                .append(Component.text("━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)));

        endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> stop(true), minutes * 60L * 20L);
    }

    public void stop(boolean announce) {
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (active == null) {
            return;
        }
        String name = active.name();
        active = null;
        core.landStats().setXpMultiplier(1.0);

        if (announce) {
            Bukkit.broadcast(Component.text("이벤트 '" + name + "'가 종료되었습니다.", NamedTextColor.GRAY));
        }
    }
}
