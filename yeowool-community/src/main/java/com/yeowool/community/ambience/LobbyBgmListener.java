package com.yeowool.community.ambience;

import com.yeowool.core.api.YeowoolCoreAPI;
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
 * for every online player who hasn't turned it off in {@code /내설정}.
 * {@code Player#playSound} plays a track once with no native loop option, so
 * this just replays it on a repeating task timed to the track's own length;
 * the small gap that can appear at the loop point is the tradeoff for not
 * needing a client-side mod. The client's own Music volume slider isn't
 * visible to the server at all, so muting it can leave a player "silent"
 * until the next scheduled replay (up to the full track length) — {@link
 * #setEnabled} exists specifically so {@code /내설정} can give an instant,
 * server-visible on/off instead.
 */
public final class LobbyBgmListener implements Listener {

    private static final String SETTING_KEY = "lobbybgm.enabled";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final String sound;
    private final float volume;
    private final long periodTicks;
    private final Map<UUID, Integer> tasks = new ConcurrentHashMap<>();

    public LobbyBgmListener(JavaPlugin plugin, YeowoolCoreAPI core, String sound, float volume, long durationSeconds) {
        this.plugin = plugin;
        this.core = core;
        this.sound = sound;
        this.volume = volume;
        this.periodTicks = durationSeconds * 20L;
    }

    public boolean isEnabled(Player player) {
        return core.playerData().getIfLoaded(player.getUniqueId())
                .map(data -> !"false".equals(data.getSetting(SETTING_KEY, "true")))
                .orElse(true);
    }

    /** Called by {@code /내설정} — starts/stops the loop immediately instead of waiting for the next scheduled replay. */
    public void setEnabled(Player player, boolean enabled) {
        core.playerData().getIfLoaded(player.getUniqueId())
                .ifPresent(data -> data.setSetting(SETTING_KEY, String.valueOf(enabled)));
        stopLoop(player);
        if (enabled) {
            startLoop(player);
        } else {
            player.stopSound(sound, SoundCategory.MUSIC);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isEnabled(event.getPlayer())) {
            startLoop(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopLoop(event.getPlayer());
    }

    private void startLoop(Player player) {
        play(player);
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> play(player), periodTicks, periodTicks);
        tasks.put(player.getUniqueId(), taskId);
    }

    private void stopLoop(Player player) {
        Integer taskId = tasks.remove(player.getUniqueId());
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
