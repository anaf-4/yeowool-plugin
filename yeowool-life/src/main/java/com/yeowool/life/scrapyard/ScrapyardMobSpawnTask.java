package com.yeowool.life.scrapyard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

/**
 * Every {@code scrapyard.mob.spawn-interval-seconds}, spawns one configured
 * MythicMobs mob (via {@code /mm mobs spawn}, same command-dispatch approach
 * as {@code OreSummonCommand} — no MythicMobs API dependency) at a random
 * registered spawn point, but only while at least one player is actually in
 * a run and the arena isn't already at {@code max-alive}. "Currently alive"
 * is counted as every non-player {@link LivingEntity} inside the registered
 * region — close enough since the arena shouldn't otherwise contain mobs,
 * and avoids needing to hook MythicMobs' own spawn-tracking API.
 */
public final class ScrapyardMobSpawnTask extends BukkitRunnable {

    private final ScrapyardSessionManager sessionManager;
    private final ScrapyardLocationStore locationStore;
    private final ScrapyardConfig config;
    private final Random random = new Random();

    public ScrapyardMobSpawnTask(ScrapyardSessionManager sessionManager, ScrapyardLocationStore locationStore, ScrapyardConfig config) {
        this.sessionManager = sessionManager;
        this.locationStore = locationStore;
        this.config = config;
    }

    @Override
    public void run() {
        if (config.mobIds().isEmpty() || !locationStore.hasRegion()) {
            return;
        }
        boolean anyoneActive = Bukkit.getOnlinePlayers().stream()
                .anyMatch(player -> sessionManager.hasActiveSession(player.getUniqueId()));
        if (!anyoneActive) {
            return;
        }

        List<ScrapyardRepository.MobSpawn> spawnPoints = locationStore.mobSpawns();
        if (spawnPoints.isEmpty()) {
            return;
        }
        Location regionWorldAnchor = locationStore.point(ScrapyardLocationStore.REGION_MIN).orElse(null);
        if (regionWorldAnchor == null || currentMobCount(regionWorldAnchor) >= config.mobMaxAlive()) {
            return;
        }

        Location spawnAt = spawnPoints.get(random.nextInt(spawnPoints.size())).toLocation();
        if (spawnAt == null) {
            return;
        }
        String mobId = config.mobIds().get(random.nextInt(config.mobIds().size()));
        String coords = spawnAt.getWorld().getName() + "," + spawnAt.getX() + "," + spawnAt.getY() + "," + spawnAt.getZ();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn " + mobId + " 1 " + coords);
    }

    private long currentMobCount(Location anyPointInWorld) {
        return anyPointInWorld.getWorld().getEntitiesByClass(LivingEntity.class).stream()
                .filter(entity -> !(entity instanceof Player))
                .filter(entity -> locationStore.isInsideRegion(entity.getLocation()))
                .count();
    }
}
