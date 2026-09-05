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
 *
 * <p>{@code customFishingId} (nullable) is a CustomFishing loot id — set only
 * for the synthetic species {@link com.yeowool.life.fishing.customfishing.CustomFishingBridge}
 * builds from that plugin's own registered loot table, so the 도감/지급 GUIs
 * can render its real item (via {@code ItemManager.buildAny}) instead of a
 * vanilla material. Mutually exclusive with {@code customIconId} in practice
 * (a species only ever comes from one source), but nothing enforces that.
 */
public record FishSpecies(String id, String name, Material material, String customIconId, String description,
                           double minSizeCm, double maxSizeCm, String customFishingId) {

    public String statisticKey() {
        return "life.fishing.catalog." + id;
    }

    /** Personal-best size for this species, stored via {@link com.yeowool.core.api.model.PlayerData#recordMaxStatistic} in millimeters (0.1cm precision). */
    public String sizeRecordStatisticKey() {
        return "life.fishing.size." + id;
    }
}
