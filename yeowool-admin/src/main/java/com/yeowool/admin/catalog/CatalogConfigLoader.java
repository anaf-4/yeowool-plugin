package com.yeowool.admin.catalog;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CatalogConfigLoader {

    private CatalogConfigLoader() {
    }

    @SuppressWarnings("unchecked")
    public static List<CatalogEntry> load(JavaPlugin plugin) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("admin-item-catalog.items")) {
            try {
                Material material = Material.valueOf(entry.get("material").toString());
                String customItemId = entry.containsKey("custom-icon") ? entry.get("custom-icon").toString() : null;
                int amount = entry.containsKey("amount") ? ((Number) entry.get("amount")).intValue() : 1;
                String name = entry.containsKey("name") ? entry.get("name").toString() : null;
                List<String> lore = entry.containsKey("lore") ? (List<String>) entry.get("lore") : List.of();
                int customModelData = entry.containsKey("custom-model-data") ? ((Number) entry.get("custom-model-data")).intValue() : -1;
                entries.add(new CatalogEntry(material, customItemId, amount, name, lore, customModelData));
            } catch (Exception e) {
                plugin.getLogger().warning("admin-item-catalog.items 항목이 잘못되었습니다: " + entry);
            }
        }
        return entries;
    }
}
