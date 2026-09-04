package com.yeowool.teleport.warp;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /워프} GUI appearance (config.yml's {@code warp-gui} section) —
 * same "decoration + custom-model-data 도트 텍스처" pattern as
 * YeowoolMarket's NPC shop decoration, so a resource pack can restyle both
 * consistently.
 */
public record WarpGuiConfig(int size, Material decorationMaterial, int decorationCustomModelData,
                             Material iconMaterial, int iconCustomModelData) {

    public boolean hasDecoration() {
        return decorationMaterial != null;
    }

    public boolean hasDecorationCustomModelData() {
        return decorationCustomModelData >= 0;
    }

    public boolean hasIconCustomModelData() {
        return iconCustomModelData >= 0;
    }

    public static WarpGuiConfig load(JavaPlugin plugin) {
        var config = plugin.getConfig();
        int size = normalizeSize(config.getInt("warp-gui.size", 27));

        Material decorationMaterial = parseMaterial(plugin, config.getString("warp-gui.decoration.material", "GRAY_STAINED_GLASS_PANE"));
        int decorationCmd = config.getInt("warp-gui.decoration.custom-model-data", -1);

        Material iconMaterial = parseMaterial(plugin, config.getString("warp-gui.icon.material", "COMPASS"));
        if (iconMaterial == null) {
            iconMaterial = Material.COMPASS;
        }
        int iconCmd = config.getInt("warp-gui.icon.custom-model-data", -1);

        return new WarpGuiConfig(size, decorationMaterial, decorationCmd, iconMaterial, iconCmd);
    }

    private static Material parseMaterial(JavaPlugin plugin, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("warp-gui 설정의 material이 잘못되었습니다: " + name);
            return null;
        }
    }

    private static int normalizeSize(int requested) {
        int rows = Math.max(1, Math.min(6, (requested + 8) / 9));
        return rows * 9;
    }
}
