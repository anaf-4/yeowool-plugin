package com.yeowool.life.farming.customcrops;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.life.farming.LifeHarvestEvent;
import net.momirealms.customcrops.api.core.block.BreakReason;
import net.momirealms.customcrops.api.event.CropBreakEvent;
import net.momirealms.customcrops.api.event.DropItemActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Bridges the (third-party) CustomCrops plugin's own crop mechanic into the
 * same rewards {@link com.yeowool.life.farming.FarmingListener} grants for
 * vanilla crops: land XP on a genuine harvest of a fully-grown crop, and the
 * server-event crop-drop multiplier ("이벤트 - 작물 드랍 2배") applied to its
 * drops. CustomCrops has its own quality/drop-table system (see its own
 * QualityCropActionEvent) that this doesn't touch — only the final amount
 * that actually gets dropped, via {@link DropItemActionEvent}.
 */
public final class CustomCropsHarvestListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerHarvest;

    public CustomCropsHarvestListener(YeowoolCoreAPI core, long xpPerHarvest) {
        this.core = core;
        this.xpPerHarvest = xpPerHarvest;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(CropBreakEvent event) {
        if (event.reason() != BreakReason.BREAK || !(event.entityBreaker() instanceof Player player)) {
            return;
        }
        var config = event.cropConfig();
        var finalStage = config.stageByPoint(config.maxPoints());
        if (finalStage == null || !finalStage.stageID().equals(event.cropStageItemID())) {
            return; // not fully grown yet - no reward, matches vanilla FarmingListener
        }
        core.landStats().addLandXp(player.getUniqueId(), xpPerHarvest);
        core.playerData().getIfLoaded(player.getUniqueId())
                .ifPresent(data -> data.addStatistic("life.farming.harvested", 1));
        Bukkit.getPluginManager().callEvent(new LifeHarvestEvent(player));
    }

    /** "이벤트 - 작물 드랍 2배" applied to every item CustomCrops itself drops. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(DropItemActionEvent event) {
        double multiplier = core.landStats().getCropDropMultiplier();
        if (multiplier == 1.0) {
            return;
        }
        var stack = event.item();
        int scaled = Math.max(1, (int) Math.round(stack.getAmount() * multiplier));
        stack.setAmount(Math.min(stack.getMaxStackSize(), scaled));
        event.item(stack);
    }
}
