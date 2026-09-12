package com.yeowool.life.scrapyard;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Every second: countdown actionbar, weight-based slowness, the boss's
 * one-time spawn once {@code scrapyard.boss.spawn-delay-seconds} into a run,
 * and the two "silent" forfeit triggers that have no event of their own —
 * time running out, and wandering outside the registered arena bounds
 * (disconnecting mid-run is instead caught immediately by {@code
 * ScrapyardListener}'s quit handler, and dying by its death handler).
 */
public final class ScrapyardTickTask extends BukkitRunnable {

    private final ScrapyardSessionManager sessionManager;
    private final ScrapyardLocationStore locationStore;
    private final ScrapyardConfig config;
    private final MessageService messages;

    public ScrapyardTickTask(ScrapyardSessionManager sessionManager, ScrapyardLocationStore locationStore,
                              ScrapyardConfig config, MessageService messages) {
        this.sessionManager = sessionManager;
        this.locationStore = locationStore;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sessionManager.hasActiveSession(player.getUniqueId())) {
                continue;
            }
            long remainingMillis = sessionManager.remainingMillis(player.getUniqueId()).orElse(0L);
            if (remainingMillis <= 0) {
                sessionManager.forfeit(player);
                messages.send(player, "scrapyard.timeout");
                continue;
            }
            if (!locationStore.isInsideRegion(player.getLocation())) {
                sessionManager.forfeit(player);
                messages.send(player, "scrapyard.left-region");
                continue;
            }

            player.sendActionBar(messages.resolveRaw("scrapyard.time-warning",
                    Placeholder.unparsed("time", DurationFormat.humanize(remainingMillis))));

            maybeSpawnBoss(player);

            int weight = sessionManager.carriedWeight(player);
            int amplifier = config.slownessAmplifierFor(weight);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            if (amplifier > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, amplifier - 1, true, false, true));
            }
        }
    }

    private void maybeSpawnBoss(Player player) {
        if (!config.hasBoss() || !sessionManager.shouldSpawnBoss(player.getUniqueId())) {
            return;
        }
        long elapsedMillis = sessionManager.elapsedMillis(player.getUniqueId()).orElse(0L);
        if (elapsedMillis < config.bossSpawnDelaySeconds() * 1000L) {
            return;
        }
        var spawnPoint = locationStore.point(ScrapyardLocationStore.BOSS_SPAWN).orElse(null);
        if (spawnPoint == null) {
            return;
        }
        sessionManager.markBossSpawned(player.getUniqueId());
        String coords = spawnPoint.getWorld().getName() + "," + spawnPoint.getX() + "," + spawnPoint.getY() + "," + spawnPoint.getZ();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn " + config.bossMobId() + " 1 " + coords);
        messages.send(player, "scrapyard.boss-spawned");
    }
}
