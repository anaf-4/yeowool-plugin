package com.yeowool.teleport.playerwarp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Optional;
import java.util.Set;

/**
 * "이동 전 안전지점(용암/낙사) 자동 체크" — the real PlayerWarps plugin's
 * {@code check-for-safe-teleport} option. Rather than outright refusing a
 * visit, scans up/down from the stored location for the nearest spot that
 * won't drop the visitor into lava/fire, suffocate them in a block, or leave
 * them falling into the void — matching how most "safe teleport" utilities
 * behave (a warp's terrain can change after it was created, e.g. someone
 * built over it with lava).
 */
public final class PlayerWarpSafety {

    private static final int SCAN_RADIUS = 12;

    private static final Set<Material> HARMFUL = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.WITHER_ROSE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH);

    private PlayerWarpSafety() {
    }

    /** Empty if no safe spot was found within {@link #SCAN_RADIUS} blocks up/down of the stored location. */
    public static Optional<Location> findSafe(Location target) {
        if (isSafe(target)) {
            return Optional.of(target);
        }
        World world = target.getWorld();
        int x = target.getBlockX();
        int z = target.getBlockZ();
        int startY = target.getBlockY();
        for (int distance = 1; distance <= SCAN_RADIUS; distance++) {
            for (int sign : new int[]{1, -1}) {
                int y = startY + distance * sign;
                if (y < world.getMinHeight() || y > world.getMaxHeight()) {
                    continue;
                }
                Location candidate = new Location(world, x + 0.5, y, z + 0.5, target.getYaw(), target.getPitch());
                if (isSafe(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block feet = world.getBlockAt(location);
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }
        if (HARMFUL.contains(feet.getType()) || HARMFUL.contains(head.getType()) || HARMFUL.contains(ground.getType())) {
            return false;
        }
        return ground.getType().isSolid() || ground.isLiquid();
    }
}
