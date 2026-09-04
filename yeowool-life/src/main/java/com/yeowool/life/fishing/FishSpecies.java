package com.yeowool.life.fishing;

import org.bukkit.Material;

/**
 * {@code customIconId} (nullable) is an ItemsAdder namespaced item id used
 * for the {@code /도감} icon when present and resolvable — same guarded
 * fallback-to-{@code material} pattern as {@code JobDefinition}. {@code
 * description} is a short flavor line shown in both the catalog and the
 * actual caught item's lore (see {@link FishingListener}); may be blank.
 * {@code minSizeCm}/{@code maxSizeCm} bound the random size rolled on every
 * catch (see {@link FishingListener#rollSizeCm}).
 */
public record FishSpecies(String id, String name, Material material, String customIconId, String description,
                           double minSizeCm, double maxSizeCm) {

    public String statisticKey() {
        return "life.fishing.catalog." + id;
    }

    /** Personal-best size for this species, stored via {@link com.yeowool.core.api.model.PlayerData#recordMaxStatistic} in millimeters (0.1cm precision). */
    public String sizeRecordStatisticKey() {
        return "life.fishing.size." + id;
    }
}
