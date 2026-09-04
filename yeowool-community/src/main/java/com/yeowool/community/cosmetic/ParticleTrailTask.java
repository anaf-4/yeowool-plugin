package com.yeowool.community.cosmetic;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Spawns each online player's equipped particle cosmetic at their feet. */
public final class ParticleTrailTask extends BukkitRunnable {

    private final YeowoolCoreAPI core;
    private final CosmeticManager manager;

    public ParticleTrailTask(YeowoolCoreAPI core, CosmeticManager manager) {
        this.core = core;
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(data ->
                    manager.equipped(data, CosmeticDefinition.Type.PARTICLE).ifPresent(cosmetic -> {
                        try {
                            Particle particle = Particle.valueOf(cosmetic.value());
                            player.getWorld().spawnParticle(particle, player.getLocation(), 3, 0.3, 0.1, 0.3, 0);
                        } catch (IllegalArgumentException ignored) {
                            // misconfigured particle name - skip silently rather than spamming the console every tick
                        }
                    }));
        }
    }
}
