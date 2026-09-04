package com.yeowool.teleport.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** One server-wide named location, admin-curated (see {@code /워프}). */
public record Warp(String name, String world, double x, double y, double z, float yaw, float pitch, UUID createdBy, long createdAt) {

    public static Warp of(String name, Location location, UUID createdBy) {
        return new Warp(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), createdBy, System.currentTimeMillis());
    }

    /** Null if the world isn't currently loaded. */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
