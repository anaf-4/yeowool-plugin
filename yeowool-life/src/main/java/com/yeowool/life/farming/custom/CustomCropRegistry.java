package com.yeowool.life.farming.custom;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses {@code custom-farming.crops} from config.yml and indexes every
 * stage's ItemsAdder namespaced id back to its {@link CustomCropDefinition}
 * and stage index, so {@link CustomFarmingListener} can look up "what crop
 * (and which stage) is this block" in O(1) from the id ItemsAdder's events
 * already hand us.
 */
public final class CustomCropRegistry {

    public record StageRef(CustomCropDefinition crop, int stageIndex) {
    }

    private final JavaPlugin plugin;
    private final Map<String, CustomCropDefinition> cropsById = new HashMap<>();
    private final Map<String, StageRef> stageIndex = new HashMap<>();

    public CustomCropRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cropsById.clear();
        stageIndex.clear();

        ConfigurationSection cropsSection = plugin.getConfig().getConfigurationSection("custom-farming.crops");
        if (cropsSection == null) {
            return;
        }

        for (String cropId : cropsSection.getKeys(false)) {
            ConfigurationSection section = cropsSection.getConfigurationSection(cropId);
            if (section == null) {
                continue;
            }
            try {
                List<CustomCropDefinition.Stage> stages = new ArrayList<>();
                for (Map<?, ?> entry : section.getMapList("stages")) {
                    String blockId = entry.get("id").toString();
                    int minutes = ((Number) entry.get("minutes")).intValue();
                    stages.add(new CustomCropDefinition.Stage(blockId, minutes));
                }
                if (stages.isEmpty()) {
                    plugin.getLogger().warning("custom-farming.crops." + cropId + "에 stages가 없습니다.");
                    continue;
                }
                long xpReward = section.getLong("xp-reward", 5);
                CustomCropDefinition crop = new CustomCropDefinition(cropId, stages, xpReward);
                cropsById.put(cropId, crop);

                for (int i = 0; i < stages.size(); i++) {
                    stageIndex.put(stages.get(i).blockId(), new StageRef(crop, i));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("custom-farming.crops." + cropId + " 설정이 잘못되었습니다: " + e.getMessage());
            }
        }

        if (!cropsById.isEmpty()) {
            plugin.getLogger().info("커스텀 작물 " + cropsById.size() + "종을 불러왔습니다: " + cropsById.keySet());
        }
    }

    public Optional<StageRef> findStage(String blockId) {
        return Optional.ofNullable(stageIndex.get(blockId));
    }

    public Optional<CustomCropDefinition> getCrop(String cropId) {
        return Optional.ofNullable(cropsById.get(cropId));
    }

    public boolean isEmpty() {
        return cropsById.isEmpty();
    }
}
