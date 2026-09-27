package com.yeowool.life.mount;

import org.bukkit.Material;

/** One MCPets pet with {@code Mountable: true}; {@code customModelData} 0 means none. */
public record MountDefinition(String id, String permission, String displayName, Material icon, int customModelData) {
}
