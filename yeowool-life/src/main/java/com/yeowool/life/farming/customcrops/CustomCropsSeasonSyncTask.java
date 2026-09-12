package com.yeowool.life.farming.customcrops;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * CustomCrops has no notion of "the same season across servers" — each of
 * lobby/town/wild runs its own CustomCrops instance with its own season
 * clock, so left alone they drift apart the moment they're not restarted at
 * the exact same wall-clock instant (already observed: 로비=겨울, 타운=여름,
 * 야생=가을 all at once). Rather than trying to keep three independent
 * in-game day counters in lockstep (each server's day/night speed, sleep
 * skipping, etc. can differ), this instead derives the "correct" season
 * purely from real wall-clock time — every server computes the exact same
 * answer independently, with no cross-server messaging needed at all, and
 * self-corrects the next time this task runs (including right after a
 * restart, however out of sync the server was before going down).
 *
 * <p>The 28-in-game-day season length from {@code CustomCrops/config.yml}
 * ({@code worlds.settings._DEFAULT_.season.duration}) is preserved by
 * assuming the vanilla day length (20 real minutes/in-game day) — so a
 * season lasts 28 * 20 = 560 real minutes here. If a server's day/night
 * cycle speed is ever changed, this real-time length no longer matches that
 * server's own in-game day count, but that doesn't matter for the sync
 * goal: every server reads the same wall clock, so they still all agree
 * with each other.
 */
public final class CustomCropsSeasonSyncTask extends BukkitRunnable {

    private static final String[] SEASON_ORDER = {"Spring", "Summer", "Autumn", "Winter"};
    private static final long SECONDS_PER_SEASON = 28L * 20L * 60L; // 28 in-game days * 20 real minutes/day

    private final JavaPlugin plugin;
    private String lastAppliedSeason;

    public CustomCropsSeasonSyncTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String canonical = canonicalSeason();
        if (canonical.equals(lastAppliedSeason)) {
            return;
        }
        lastAppliedSeason = canonical;

        String worldName = Bukkit.getWorlds().get(0).getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "customcrops season set " + worldName + " " + canonical);
        plugin.getLogger().info("[CustomCrops 계절 동기화] " + worldName + " 계절을 " + canonical + "(으)로 맞췄습니다.");
    }

    private static String canonicalSeason() {
        long epochSeconds = System.currentTimeMillis() / 1000L;
        int index = (int) ((epochSeconds / SECONDS_PER_SEASON) % SEASON_ORDER.length);
        return SEASON_ORDER[index];
    }
}
