package com.yeowool.life.farming;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;

/**
 * Section 6.1 (YeowoolFarming) of the plugin plan: supported crops = 밀,
 * 당근, 감자, 비트, 호박, 수박, 코코아, 네더 사마귀, 사탕수수, 달콤한 열매.
 * 대나무/선인장/켈프 is intentionally excluded — no handler means those stay
 * fully vanilla. Harvest XP feeds land XP directly (see plugin plan 4.3:
 * "XP 획득 - 농사").
 */
public final class FarmingListener implements Listener {

    /**
     * Every material {@link #onPlant} starts a forced growth timer for. Pumpkin/melon fruit
     * blocks aren't {@link Ageable} at all, and sugar cane's own age isn't a reliable "has this
     * specific block sat long enough" signal on its own - all three get exactly the same
     * CropTimerService-driven wait as the truly age-based crops instead (matches this class's own
     * flat "모든 작물 성장시간 = 10분" rule, which the plan already lists 사탕수수 under), so a
     * place → break → replant loop can't bypass the wait and farm XP/stats instantly.
     */
    private static final Set<Material> AGEABLE_CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.COCOA, Material.NETHER_WART, Material.SWEET_BERRY_BUSH,
            Material.PUMPKIN_STEM, Material.MELON_STEM,
            Material.PUMPKIN, Material.MELON, Material.SUGAR_CANE
    );

    private static final Set<Material> HARVEST_XP_MATERIALS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.COCOA, Material.NETHER_WART, Material.SWEET_BERRY_BUSH,
            Material.PUMPKIN, Material.MELON, Material.SUGAR_CANE
    );

    private final YeowoolCoreAPI core;
    private final CropTimerService timerService;
    private final long xpPerHarvest;

    public FarmingListener(YeowoolCoreAPI core, CropTimerService timerService, long xpPerHarvest) {
        this.core = core;
        this.timerService = timerService;
        this.xpPerHarvest = xpPerHarvest;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlant(BlockPlaceEvent event) {
        if (AGEABLE_CROPS.contains(event.getBlock().getType())) {
            timerService.startTimer(event.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (timerService.isPending(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        // Read before cancel() removes it from the pending map, or this always sees "not pending".
        boolean stillPending = timerService.isPending(block);
        if (AGEABLE_CROPS.contains(type)) {
            timerService.cancel(block);
        }

        if (!HARVEST_XP_MATERIALS.contains(type)) {
            return;
        }
        boolean immature = block.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge();
        if (immature || stillPending) {
            // Not fully grown yet, or (pumpkin/melon/sugar cane, which aren't reliably gated by
            // their own Ageable state) hand-placed and still within the forced growth window.
            return;
        }

        Player player = event.getPlayer();
        core.landStats().addLandXp(player.getUniqueId(), xpPerHarvest);
        core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(playerData -> {
            playerData.addStatistic("life.farming.harvested", 1);
            playerData.addStatistic("dex.farming." + type.name(), 1);
        });
    }
}
