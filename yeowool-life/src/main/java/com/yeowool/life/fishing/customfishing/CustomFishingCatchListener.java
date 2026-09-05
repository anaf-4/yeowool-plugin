package com.yeowool.life.fishing.customfishing;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.life.fishing.FishSpecies;
import com.yeowool.life.fishing.FishingCompetitionManager;
import com.yeowool.life.job.JobManager;
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.event.FishingLootSpawnEvent;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Fishing itself now happens entirely through CustomFishing's own mechanic
 * (bobber/minigame) instead of our {@link com.yeowool.life.fishing.FishingListener}
 * — see {@code YeowoolLife#onEnable}, which only registers that vanilla-event
 * listener as a fallback when CustomFishing isn't installed. Everything
 * *around* a catch (어부 job XP, 땅 XP, the fishing-discovered statistic used
 * by the 도감 "???" gating, and the daily 낚시대회) still needs to fire the
 * same way regardless of which plugin actually caught the fish, so this
 * listener re-derives all of it from {@link FishingLootSpawnEvent} — the one
 * CustomFishing event that hands over the real spawned {@link Item} entity,
 * which is what makes the caught size available via {@code getFishSize}.
 */
public final class CustomFishingCatchListener implements Listener {

    private final YeowoolCoreAPI core;
    private final JobManager jobManager;
    private final long xpPerCatch;
    private final FishingCompetitionManager competitionManager;

    public CustomFishingCatchListener(YeowoolCoreAPI core, JobManager jobManager, long xpPerCatch,
                                       FishingCompetitionManager competitionManager) {
        this.core = core;
        this.jobManager = jobManager;
        this.xpPerCatch = xpPerCatch;
        this.competitionManager = competitionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootSpawn(FishingLootSpawnEvent event) {
        Loot loot = event.getLoot();
        if (loot == null || loot.type() != LootType.ITEM) {
            return;
        }
        Player player = event.getContext().holder();
        if (player == null) {
            return;
        }

        core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(data -> {
            data.addStatistic("life.fishing.caught", 1);
            data.addStatistic("life.fishing.catalog." + loot.id(), 1);
        });

        core.landStats().addLandXp(player.getUniqueId(), xpPerCatch);
        jobManager.grantXp(player, "fisherman", xpPerCatch);

        if (event.getEntity() instanceof Item itemEntity) {
            Float sizeCm = BukkitCustomFishingPlugin.getInstance().getItemManager().getFishSize(itemEntity.getItemStack());
            if (sizeCm != null) {
                long sizeMm = Math.round(sizeCm * 10);
                core.playerData().getIfLoaded(player.getUniqueId())
                        .ifPresent(data -> data.recordMaxStatistic("life.fishing.size." + loot.id(), sizeMm));
                competitionManager.recordCatch(player, loot.id(), sizeCm);
            }
        }
    }
}
