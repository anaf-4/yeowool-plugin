package com.yeowool.community.event;

import com.yeowool.core.api.YeowoolCoreAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A concrete slice of the plugin plan's "몹 처치/아이템 수집" mission-event
 * idea: one server-wide goal (kill N of an entity type, or collect N of a
 * material) that every player races individually — first to hit the goal
 * gets the reward, everyone keeps their own progress otherwise. Separate
 * from {@link EventManager}'s XP-multiplier events so both kinds can run at
 * once (e.g. "2x XP weekend" plus a one-off mission).
 */
public final class MissionEventManager {

    public enum Kind { KILL, COLLECT }

    public record ActiveMission(Kind kind, String targetId, int goal, long endAtMillis) {
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private volatile ActiveMission active;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    private BukkitTask endTask;

    public MissionEventManager(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    public Optional<ActiveMission> active() {
        return Optional.ofNullable(active);
    }

    public boolean isActive() {
        return active != null;
    }

    public int progressFor(UUID player) {
        return progress.getOrDefault(player, 0);
    }

    public void start(Kind kind, String targetId, int goal, int minutes) {
        stop(false);
        active = new ActiveMission(kind, targetId, goal, System.currentTimeMillis() + minutes * 60_000L);
        progress.clear();

        String verb = kind == Kind.KILL ? "처치" : "수집";
        Bukkit.broadcast(Component.text("━━━━━━━━━━━━━━━━━━\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("🎯 미션 이벤트 시작: " + targetId + " " + goal + "개 " + verb + "!\n", NamedTextColor.GOLD))
                .append(Component.text("가장 먼저 달성한 플레이어에게 보상이 지급됩니다. (" + minutes + "분간) /이벤트 미션정보 로 진행도를 확인하세요.\n", NamedTextColor.YELLOW))
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
        String targetId = active.targetId();
        active = null;
        progress.clear();
        if (announce) {
            Bukkit.broadcast(Component.text("미션 이벤트 '" + targetId + "'가 종료되었습니다.", NamedTextColor.GRAY));
        }
    }

    /** Called by the kill/collect listeners on every matching action. Grants the reward and ends the mission the moment someone reaches the goal. */
    public void increment(Player player, Kind kind, String targetId) {
        ActiveMission mission = active;
        if (mission == null || mission.kind() != kind || !mission.targetId().equalsIgnoreCase(targetId)) {
            return;
        }
        int updated = progress.merge(player.getUniqueId(), 1, Integer::sum);
        if (updated < mission.goal()) {
            return;
        }

        active = null;
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        progress.clear();

        long rewardOn = plugin.getConfig().getLong("mission.reward-on", 0);
        if (rewardOn > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), rewardOn, "YeowoolCommunity",
                    "미션 이벤트 달성: " + targetId);
        }
        for (Map<?, ?> entry : plugin.getConfig().getMapList("mission.reward-items")) {
            try {
                Material material = Material.valueOf(entry.get("material").toString());
                int amount = ((Number) entry.get("amount")).intValue();
                var leftover = player.getInventory().addItem(new ItemStack(material, amount));
                leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            } catch (Exception e) {
                plugin.getLogger().warning("mission.reward-items 항목이 잘못되었습니다: " + entry);
            }
        }

        core.sounds().play(player, "levelup");
        Bukkit.broadcast(Component.text("🏆 " + player.getName() + "님이 미션 '" + targetId + "'을(를) 가장 먼저 달성하여 보상을 받았습니다!", NamedTextColor.GOLD));
    }
}
