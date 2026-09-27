package com.yeowool.life.mining;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Section 6.5 (YeowoolMining): mining itself stays vanilla (plan explicitly
 * says to preserve the vanilla feel) — this only adds XP/statistics tracking
 * on ore breaks.
 */
public final class MiningListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerOre;
    /** Ores a player placed (e.g. mined with Silk Touch and put back) — breaking them again earns nothing. */
    private final Set<Location> playerPlacedOres = ConcurrentHashMap.newKeySet();

    public MiningListener(YeowoolCoreAPI core, long xpPerOre) {
        this.core = core;
        this.xpPerOre = xpPerOre;
    }

    private boolean isOre(Material material) {
        return material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isOre(event.getBlock().getType())) {
            playerPlacedOres.add(event.getBlock().getLocation());
        }
    }

    /**
     * Drops tracked locations whose block is no longer an ore (exploded, pushed, replaced...), only in
     * loaded chunks so pruning never force-loads the world. Called periodically from {@code YeowoolLife}.
     */
    public void pruneStaleEntries() {
        playerPlacedOres.removeIf(location -> {
            var world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return false;
            }
            return !isOre(location.getBlock().getType());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isOre(event.getBlock().getType())) {
            return;
        }
        if (playerPlacedOres.remove(event.getBlock().getLocation())) {
            return; // ponytail: in-memory like LoggingListener's placed logs — a restart forgets placements.
        }
        Player player = event.getPlayer();
        core.landStats().addLandXp(player.getUniqueId(), xpPerOre);
        core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(data -> {
            data.addStatistic("life.mining.mined", 1);
            data.addStatistic("dex.mining." + event.getBlock().getType().name(), 1);
        });

        Bukkit.getPluginManager().callEvent(new PlayerRepeatableActionEvent(player.getUniqueId(), "mining"));
    }
}
