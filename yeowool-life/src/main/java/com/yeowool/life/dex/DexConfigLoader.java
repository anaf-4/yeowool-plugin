package com.yeowool.life.dex;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads {@code collection-dex.<category>} lists from {@code config.yml} — same shape for 광물/사냥/작물. */
public final class DexConfigLoader {

    private DexConfigLoader() {
    }

    public static List<DexEntry> load(JavaPlugin plugin, String category) {
        List<DexEntry> entries = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("collection-dex." + category)) {
            try {
                String id = entry.get("id").toString();
                String display = entry.get("display").toString();
                Material icon = Material.valueOf(entry.get("material").toString());
                entries.add(new DexEntry(id, display, icon));
            } catch (Exception e) {
                plugin.getLogger().warning("collection-dex." + category + " 설정 항목이 잘못되었습니다: " + entry);
            }
        }
        return List.copyOf(entries);
    }
}
