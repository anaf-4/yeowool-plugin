package com.yeowool.community.ambience;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loops a background music track (ItemsAdder custom sound, {@code
 * lobby-bgm.enabled} in config.yml — only the lobby server turns this on)
 * for every online player. {@code Player#playSound} plays a track once with
 * no native loop option, so this just replays it on a repeating task timed
 * to the track's own length; the small gap that can appear at the loop
 * point is the tradeoff for not needing a client-side mod.
 */
public final class LobbyBgmListener implements Listener {

    private final String sound;
    private final float volume;
    private final long periodTicks;
    private final Map<UUID, Integer> tasks = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    public LobbyBgmListener(JavaPlugin plugin, String sound, float volume, long durationSeconds) {
        this.plugin = plugin;
        this.sound = sound;
        this.volume = volume;
        this.periodTicks = durationSeconds * 20L;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        play(player);
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> play(player), periodTicks, periodTicks);
        tasks.put(player.getUniqueId(), taskId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Integer taskId = tasks.remove(event.getPlayer().getUniqueId());
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    private void play(Player player) {
        if (player.isOnline()) {
            player.playSound(player.getLocation(), sound, SoundCategory.MUSIC, volume, 1f);
        }
    }
}
