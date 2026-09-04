package com.yeowool.admin.catalog;

import org.bukkit.Material;

/**
 * One row of {@code /관리자아이템}'s catalog — server-specific items admins
 * need quick access to (e.g. the land-claim barrel, or tiered fishing gear
 * otherwise only reachable via ItemsAdder's own {@code /iagive}) without
 * digging through creative mode. {@code displayName}/{@code lore} may be
 * null to just use the material's own vanilla name. {@code customItemId}
 * (nullable) is an ItemsAdder namespaced item id, preferred over
 * {@code material} when present and resolvable — same guarded
 * fallback-to-{@code material} pattern as {@code JobDefinition}/
 * {@code FishSpecies}, so this module still loads fine without ItemsAdder.
 */
public record CatalogEntry(Material material, String customItemId, int amount, String displayName, java.util.List<String> lore, int customModelData) {

    public boolean hasCustomModelData() {
        return customModelData >= 0;
    }

    public boolean isCustomItem() {
        return customItemId != null && !customItemId.isBlank();
    }
}
