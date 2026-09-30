package com.yeowool.life.mining;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import com.yeowool.life.metals.MetalService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Section 6.5 (YeowoolMining): mining itself stays vanilla (plan explicitly
 * says to preserve the vanilla feel) — this only adds XP/statistics tracking
 * on ore breaks. Also the entry point for 판타지 금속 원석 drops ({@link MetalService}), so those share this
 * player-placed-block exclusion.
 */
public final class MiningListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerOre;
    /**
     * Ores a player placed (e.g. mined with Silk Touch and put back) — breaking them again earns nothing — plus, in metal
     * worlds, the stone-type blocks that can drop 원석 ({@link MetalService#tracksPlaced}) — placed, or formed by a
     * lava/water generator (cobblestone/stone/basalt farms). Follows blocks that pistons move.
     */
    private final Set<Location> playerPlacedOres = ConcurrentHashMap.newKeySet();
    private MetalService metals;
    /** Above this many entries, pruning also forgets entries in unloaded chunks. */
    private static final int PRUNE_UNLOADED_ABOVE = 100_000;

    public MiningListener(YeowoolCoreAPI core, long xpPerOre) {
        this.core = core;
        this.xpPerOre = xpPerOre;
    }

    /** Set once the 대장간 is enabled on this server. */
    public void setMetals(MetalService metals) {
        this.metals = metals;
    }

    public static boolean isOre(Material material) {
        return material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE");
    }

    private boolean tracked(Block block) {
        return isOre(block.getType()) || metals != null && metals.tracksPlaced(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tracked(event.getBlock())) {
            playerPlacedOres.add(event.getBlock().getLocation());
        }
    }

    /** Generator-formed blocks (lava + water → stone/basalt/...) count as placed — the block isn't there yet, so check the new type. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (metals != null && metals.tracksType(block.getWorld(), event.getNewState().getType())) {
            playerPlacedOres.add(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        moveTracked(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        moveTracked(event.getBlocks(), event.getDirection()); // direction = where the pulled blocks move
    }

    /** Remove every moving tracked location first, then re-add at the destination, so chained blocks don't clobber each other. */
    private void moveTracked(List<Block> blocks, BlockFace direction) {
        List<Location> destinations = new ArrayList<>();
        for (Block block : blocks) {
            if (playerPlacedOres.remove(block.getLocation())) {
                destinations.add(block.getRelative(direction).getLocation());
            }
        }
        playerPlacedOres.addAll(destinations);
    }

    /**
     * Drops tracked locations whose block is no longer tracked-type (exploded, replaced...), only in loaded chunks so
     * pruning never force-loads the world; past {@link #PRUNE_UNLOADED_ABOVE} entries it also forgets unloaded chunks'.
     * Called periodically from {@code YeowoolLife}.
     */
    public void pruneStaleEntries() {
        boolean forgetUnloaded = playerPlacedOres.size() > PRUNE_UNLOADED_ABOVE;
        playerPlacedOres.removeIf(location -> {
            if (!location.isWorldLoaded()) {
                return true;
            }
            var world = location.getWorld();
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return forgetUnloaded; // ponytail: size cap forgets far-away placements; persist per-chunk PDC if that's ever farmed
            }
            return !tracked(location.getBlock());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (playerPlacedOres.remove(event.getBlock().getLocation())) {
            return; // ponytail: in-memory like LoggingListener's placed logs — a restart forgets placements.
        }
        if (metals != null) {
            metals.onNaturalBreak(event);
        }
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
