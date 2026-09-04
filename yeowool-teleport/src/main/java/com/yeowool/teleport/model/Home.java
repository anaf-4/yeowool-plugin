package com.yeowool.teleport.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** One player-owned named location (see {@code /홈}). */
public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {

    public static Home of(String name, Location location) {
        return new Home(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    /** Null if the world isn't currently loaded (e.g. removed since the home was set). */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
