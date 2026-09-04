package com.yeowool.life.job;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parses {@code config.yml}'s {@code jobs.list} and {@code jobs.skills} sections. */
public final class JobConfigLoader {

    private JobConfigLoader() {
    }

    public static Map<String, JobDefinition> loadJobs(JavaPlugin plugin) {
        Map<String, JobDefinition> jobs = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("jobs.list");
        if (section == null) {
            plugin.getLogger().warning("jobs.list 설정이 없습니다.");
            return jobs;
        }
        for (String id : section.getKeys(false)) {
            String display = section.getString(id + ".display", id);
            String description = section.getString(id + ".description", "");
            Material icon = parseMaterial(plugin, id, section.getString(id + ".icon", "BOOK"));
            String customIconId = section.getString(id + ".custom-icon", null);
            jobs.put(id, new JobDefinition(id, display, icon, customIconId, description));
        }
        return jobs;
    }

    private static Material parseMaterial(JavaPlugin plugin, String jobId, String name) {
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("jobs.list." + jobId + ".icon이 잘못되었습니다: " + name + " - BOOK으로 대체합니다.");
            return Material.BOOK;
        }
    }

    public static Map<String, SkillNode> loadSkills(JavaPlugin plugin) {
        Map<String, SkillNode> skills = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("jobs.skills");
        if (section == null) {
            return skills;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            try {
                String display = node.getString("display", id);
                int maxPoints = node.getInt("max-points", 10);
                SkillEffectType effect = SkillEffectType.valueOf(node.getString("effect"));
                double perPoint = node.getDouble("per-point");
                skills.put(id, new SkillNode(id, display, maxPoints, effect, perPoint));
            } catch (Exception e) {
                plugin.getLogger().warning("jobs.skills." + id + " 설정이 잘못되었습니다: " + e.getMessage());
            }
        }
        return skills;
    }
}
