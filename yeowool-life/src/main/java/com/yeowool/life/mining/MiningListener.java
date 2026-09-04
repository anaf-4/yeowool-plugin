package com.yeowool.life.mining;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Section 6.5 (YeowoolMining): mining itself stays vanilla (plan explicitly
 * says to preserve the vanilla feel) — this only adds XP/statistics tracking
 * on ore breaks.
 */
public final class MiningListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerOre;

    public MiningListener(YeowoolCoreAPI core, long xpPerOre) {
        this.core = core;
        this.xpPerOre = xpPerOre;
    }

    private boolean isOre(Material material) {
        return material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isOre(event.getBlock().getType())) {
            return;
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
