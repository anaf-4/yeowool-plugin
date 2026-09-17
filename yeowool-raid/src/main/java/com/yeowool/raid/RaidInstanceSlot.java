package com.yeowool.raid;

import org.bukkit.Location;

public record RaidInstanceSlot(
        long id,
        long raidId,
        int slotIndex,
        Location entry,
        Location bossSpawn,
        Location exit,
        Location boundMin,
        Location boundMax
) {
    public boolean contains(Location location) {
        if (!location.getWorld().equals(boundMin.getWorld())) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= Math.min(boundMin.getX(), boundMax.getX()) && x <= Math.max(boundMin.getX(), boundMax.getX())
                && y >= Math.min(boundMin.getY(), boundMax.getY()) && y <= Math.max(boundMin.getY(), boundMax.getY())
                && z >= Math.min(boundMin.getZ(), boundMax.getZ()) && z <= Math.max(boundMin.getZ(), boundMax.getZ());
    }
}
