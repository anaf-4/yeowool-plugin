package com.yeowool.life.farming.custom;

import com.yeowool.core.api.YeowoolCoreAPI;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockPlaceEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final long incomePerHarvest;
    private final CustomFarmingQualityConfig quality;

    public CustomFarmingListener(YeowoolCoreAPI core, CustomCropRegistry registry, CustomCropTimerService timerService,
                                  long incomePerHarvest, CustomFarmingQualityConfig quality) {
        this.core = core;
        this.registry = registry;
        this.timerService = timerService;
        this.incomePerHarvest = incomePerHarvest;
        this.quality = quality;
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

        if (incomePerHarvest > 0) {
            CustomFarmingQualityConfig.Grade grade = quality.roll();
            long income = Math.round(incomePerHarvest * quality.multiplierFor(grade));
            core.economyData().modifyBalance(player.getUniqueId(), income, "YeowoolLife", "직업 소득: 커스텀 농사 (" + grade + ")");
            switch (grade) {
                case GOLD -> player.sendMessage(Component.text("금별 작물을 수확했습니다! (+" + income + "온)", NamedTextColor.GOLD));
                case SILVER -> player.sendMessage(Component.text("은별 작물을 수확했습니다! (+" + income + "온)", NamedTextColor.AQUA));
                case NORMAL -> { }
            }
        }
    }
}
