package com.yeowool.teleport;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared teleport-safety plumbing (delay before moving, cancel on
 * movement, cooldown between teleports) that every teleport command in this
 * plugin (/홈, /워프, /tpa 수락) funnels through, so the rules only live in
 * one place instead of being copy-pasted per command.
 */
public final class TeleportService {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final int delaySeconds;
    private final boolean cancelOnMove;
    private final long cooldownMillis;

    private final Map<UUID, Long> lastTeleportAt = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingTasks = new ConcurrentHashMap<>();

    public TeleportService(JavaPlugin plugin, MessageService messages, int delaySeconds, boolean cancelOnMove, long cooldownMillis) {
        this.plugin = plugin;
        this.messages = messages;
        this.delaySeconds = delaySeconds;
        this.cancelOnMove = cancelOnMove;
        this.cooldownMillis = cooldownMillis;
    }

    public boolean cancelsOnMove() {
        return cancelOnMove;
    }

    /** Seconds remaining before {@code player} can teleport again, or 0 if they're free to. */
    public long remainingCooldownSeconds(Player player) {
        Long last = lastTeleportAt.get(player.getUniqueId());
        if (last == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        return elapsed >= cooldownMillis ? 0 : (cooldownMillis - elapsed + 999) / 1000;
    }

    public boolean hasPending(UUID uuid) {
        return pendingTasks.containsKey(uuid);
    }

    /** Cancels {@code player}'s countdown (if any) without teleporting them. */
    public void cancelPending(UUID uuid) {
        var task = pendingTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /** Starts the delay countdown (or teleports immediately if delay is 0) and applies the cooldown once it lands. */
    public void requestTeleport(Player player, Location destination) {
        cancelPending(player.getUniqueId());

        if (delaySeconds <= 0) {
            doTeleport(player, destination);
            return;
        }

        String key = cancelOnMove ? "teleport.countdown-cancellable" : "teleport.countdown";
        messages.send(player, key, Placeholder.unparsed("seconds", String.valueOf(delaySeconds)));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTasks.remove(player.getUniqueId());
            doTeleport(player, destination);
        }, delaySeconds * 20L);
        pendingTasks.put(player.getUniqueId(), task);
    }

    private void doTeleport(Player player, Location destination) {
        player.teleportAsync(destination);
        lastTeleportAt.put(player.getUniqueId(), System.currentTimeMillis());
        messages.send(player, "teleport.moved");
    }
}
