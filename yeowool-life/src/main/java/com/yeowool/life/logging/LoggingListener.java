package com.yeowool.life.logging;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Section 6.2 (YeowoolLogging): vanilla tree species only, XP + statistics
 * on chop. Player-placed logs are tracked so breaking a log someone placed
 * (instead of one that grew naturally) doesn't farm XP.
 */
public final class LoggingListener implements Listener {

    private final YeowoolCoreAPI core;
    private final long xpPerLog;
    private final Set<Location> playerPlacedLogs = ConcurrentHashMap.newKeySet();

    public LoggingListener(YeowoolCoreAPI core, long xpPerLog) {
        this.core = core;
        this.xpPerLog = xpPerLog;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (Tag.LOGS.isTagged(event.getBlock().getType())) {
            playerPlacedLogs.add(event.getBlock().getLocation());
        }
    }

    /**
     * A player-placed log that's never broken through {@link #onBreak} (built into a structure,
     * burned, exploded, removed by WorldEdit/another plugin, ...) would otherwise sit in {@link
     * #playerPlacedLogs} forever - self-heals by dropping any tracked location whose block isn't
     * a log anymore. Meant to be called periodically (see {@code YeowoolLife}); only touches
     * already-loaded chunks so it can't force-load the world just to prune.
     */
    public void pruneStaleEntries() {
        playerPlacedLogs.removeIf(location -> {
            var world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return false;
            }
            return !Tag.LOGS.isTagged(location.getBlock().getType());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!Tag.LOGS.isTagged(event.getBlock().getType())) {
            return;
        }
        Location location = event.getBlock().getLocation();
        if (playerPlacedLogs.remove(location)) {
            return;
        }

        Player player = event.getPlayer();
        core.landStats().addLandXp(player.getUniqueId(), xpPerLog);
        core.playerData().getIfLoaded(player.getUniqueId())
                .ifPresent(data -> data.addStatistic("life.logging.chopped", 1));
    }
}
