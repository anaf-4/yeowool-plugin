package com.yeowool.teleport.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** One player-owned, publicly browsable warp (see {@code /플레이어워프}) — richer than admin {@link Warp}: category, rating, price, favorites. */
public record PlayerWarp(UUID owner, String name, String world, double x, double y, double z, float yaw, float pitch,
                          long createdAt, String displayName, String description, String category, Status status,
                          String previewItemId, long price, long visits) {

    public enum Status { OPENED, CLOSED }

    public static PlayerWarp create(UUID owner, String name, Location location) {
        return new PlayerWarp(owner, name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), System.currentTimeMillis(), null, null, "none", Status.OPENED, null, 0L, 0L);
    }

    public String effectiveDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName : name;
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public PlayerWarp withLocation(Location location) {
        return new PlayerWarp(owner, name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), createdAt, displayName, description, category, status, previewItemId, price, visits);
    }

    public PlayerWarp withName(String newName) {
        return new PlayerWarp(owner, newName, world, x, y, z, yaw, pitch, createdAt, displayName, description, category, status, previewItemId, price, visits);
    }

    public PlayerWarp withOwner(UUID newOwner) {
        return new PlayerWarp(newOwner, name, world, x, y, z, yaw, pitch, createdAt, displayName, description, category, status, previewItemId, price, visits);
    }

    public PlayerWarp withDisplayName(String newDisplayName) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, newDisplayName, description, category, status, previewItemId, price, visits);
    }

    public PlayerWarp withDescription(String newDescription) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, displayName, newDescription, category, status, previewItemId, price, visits);
    }

    public PlayerWarp withCategory(String newCategory) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, displayName, description, newCategory, status, previewItemId, price, visits);
    }

    public PlayerWarp withStatus(Status newStatus) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, displayName, description, category, newStatus, previewItemId, price, visits);
    }

    public PlayerWarp withPreviewItemId(String newPreviewItemId) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, displayName, description, category, status, newPreviewItemId, price, visits);
    }

    public PlayerWarp withPrice(long newPrice) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, displayName, description, category, status, previewItemId, newPrice, visits);
    }

    public PlayerWarp withVisits(long newVisits) {
        return new PlayerWarp(owner, name, world, x, y, z, yaw, pitch, createdAt, displayName, description, category, status, previewItemId, price, newVisits);
    }
}
