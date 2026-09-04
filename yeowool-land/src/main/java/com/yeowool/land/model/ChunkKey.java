package com.yeowool.land.model;

import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Identity of a single chunk, independent of whether it is currently loaded.
 * Used as the in-memory index key and the DB row key for claimed chunks.
 */
public record ChunkKey(String world, int x, int z) {

    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static ChunkKey of(World world, int blockX, int blockZ) {
        return new ChunkKey(world.getName(), blockX >> 4, blockZ >> 4);
    }
}
