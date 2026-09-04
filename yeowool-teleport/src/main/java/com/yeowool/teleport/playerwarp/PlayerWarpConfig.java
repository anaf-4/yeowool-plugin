package com.yeowool.teleport.playerwarp;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code config.yml}'s {@code player-warp} section — fee amounts (0 disables), limits, categories, banned preview items. Mirrors the real PlayerWarps plugin's own {@code config.yml} fields 1:1. */
public record PlayerWarpConfig(long createFee, long renameFee, long relocateFee, long transferOwnershipFee,
                                long setPriceFee, long setCategoryFee, long setPreviewItemFee, long setDescriptionFee,
                                long setDisplayNameFee, long setAccessibilityFee, long deleteRefund, long maxAdmission,
                                int nameMaxLength, int displayNameMaxLength, int descriptionMaxLength,
                                Set<Material> bannedPreviewItems, List<PlayerWarpCategory> categories,
                                boolean checkSafeTeleport, int backgroundOffsetPx) {

    public static PlayerWarpConfig load(FileConfiguration config) {
        var root = config.getConfigurationSection("player-warp");
        if (root == null) {
            return defaults();
        }
        Set<Material> banned = new HashSet<>();
        for (String name : root.getStringList("banned-preview-items")) {
            try {
                banned.add(Material.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // skip unknown material name
            }
        }
        List<PlayerWarpCategory> categories = new ArrayList<>();
        for (Map<?, ?> raw : root.getMapList("categories")) {
            categories.add(new PlayerWarpCategory(String.valueOf(raw.get("id")), String.valueOf(raw.get("name")), String.valueOf(raw.get("icon"))));
        }
        return new PlayerWarpConfig(
                root.getLong("create-fee", 5000),
                root.getLong("rename-fee", 500),
                root.getLong("relocate-fee", 1000),
                root.getLong("transfer-ownership-fee", 0),
                root.getLong("set-price-fee", 0),
                root.getLong("set-category-fee", 0),
                root.getLong("set-preview-item-fee", 0),
                root.getLong("set-description-fee", 0),
                root.getLong("set-display-name-fee", 200),
                root.getLong("set-accessibility-fee", 0),
                root.getLong("delete-refund", 2000),
                root.getLong("max-admission", 100_000),
                root.getInt("name-max-length", 24),
                root.getInt("display-name-max-length", 32),
                root.getInt("description-max-length", 50),
                banned,
                categories,
                root.getBoolean("check-safe-teleport", true),
                config.getInt("player-warp-gui.gui-background-offset", -8)
        );
    }

    private static PlayerWarpConfig defaults() {
        return new PlayerWarpConfig(5000, 500, 1000, 0, 0, 0, 0, 0, 200, 0, 2000, 100_000, 24, 32, 50, Set.of(), List.of(), true, -8);
    }
}
