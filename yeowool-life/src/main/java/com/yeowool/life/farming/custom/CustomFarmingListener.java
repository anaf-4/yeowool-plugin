package com.yeowool.life.farming.custom;

import com.yeowool.core.api.YeowoolCoreAPI;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockPlaceEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Bridges ItemsAdder's custom block events into {@link CustomCropTimerService}:
 * planting a crop's first stage starts its growth chain, and breaking its
 * final (harvestable) stage grants XP/statistics the same way vanilla crops
 * do in {@link com.yeowool.life.farming.FarmingListener} — the actual item
 * drop is handled by ItemsAdder itself, this only reacts to it.
 */
public final class CustomFarmingListener implements Listener {

    private final YeowoolCoreAPI core;
    private final CustomCropRegistry registry;
    private final CustomCropTimerService timerService;

    public CustomFarmingListener(YeowoolCoreAPI core, CustomCropRegistry registry, CustomCropTimerService timerService) {
        this.core = core;
        this.registry = registry;
        this.timerService = timerService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(CustomBlockPlaceEvent event) {
        registry.findStage(event.getNamespacedID())
                .filter(ref -> ref.stageIndex() == 0)
                .ifPresent(ref -> timerService.onPlanted(event.getBlock(), ref.crop()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(CustomBlockBreakEvent event) {
        var ref = registry.findStage(event.getNamespacedID()).orElse(null);
        if (ref == null) {
            return;
        }

        timerService.cancel(event.getBlock());

        if (!ref.crop().isFinalStage(ref.stageIndex())) {
            return; // broken before fully grown - no reward, matches vanilla FarmingListener
        }

        Player player = event.getPlayer();
        core.landStats().addLandXp(player.getUniqueId(), ref.crop().xpReward());
        core.playerData().getIfLoaded(player.getUniqueId())
                .ifPresent(data -> data.addStatistic("life.farming.harvested", 1));
    }
}
