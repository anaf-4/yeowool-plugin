package com.yeowool.life.cooking.addcook;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads AddCook's own recipe files ({@code contents/recipes/*.yml}) directly rather than going
 * through its public API — the shipped {@code AddCookAPI} facade (packages {@code
 * com.github.teamhungry22.addcook.api}/{@code .api.event}) only exposes item/entity/furniture
 * lookups, nothing about recipes at all, and the {@code .api.line}/{@code .api.model} packages
 * don't exist in the deployed 3.8.2 jar. Decompiling the plugin's internal (non-API)
 * {@code PlayerInteractListener} showed how "knowing" a recipe actually works: using a recipe
 * book item grants the player the LuckPerms node {@code addcook.recipe.<id>} permanently
 * (see {@code PlayerUtils.addPermission}) — there's no separate "known recipes" list anywhere,
 * the permission itself IS the record. So everything else we need — ingredients and results —
 * also just comes straight out of these same config files.
 */
public final class AddCookRecipeIndex {

    /** One ingredient slot's alternatives — usually a single id, sometimes "any of these" (e.g. any raw fish). */
    public record IngredientOption(String itemId) {
    }

    /** One possible result of cooking (일반/은별/금별 tier, or a single flat result), with its drop weight. */
    public record ResultTier(String itemId, int amount, int weight) {
    }

    public record RecipeEntry(String id, String displayName, String furnitureLabel, String iconId,
                               List<List<IngredientOption>> stages, List<ResultTier> results) {
        public String permission() {
            return "addcook.recipe." + id;
        }
    }

    private static final Map<String, String> FURNITURE_LABELS = Map.of(
            "chopping_board", "도마",
            "fryer", "튀김기",
            "frypan", "프라이팬",
            "pot", "냄비"
    );

    private AddCookRecipeIndex() {
    }

    public static List<RecipeEntry> load() {
        List<RecipeEntry> entries = new ArrayList<>();
        Plugin addCook = Bukkit.getPluginManager().getPlugin("AddCook");
        if (addCook == null) {
            return entries;
        }
        File recipesDir = new File(addCook.getDataFolder(), "contents/recipes");
        File[] files = recipesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return entries;
        }
        for (File file : files) {
            String furnitureKey = file.getName().substring(0, file.getName().length() - ".yml".length());
            String furnitureLabel = FURNITURE_LABELS.getOrDefault(furnitureKey, furnitureKey);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String id : yaml.getKeys(false)) {
                if (id.startsWith("test_")) {
                    continue;
                }
                ConfigurationSection section = yaml.getConfigurationSection(id);
                if (section == null || !section.getBoolean("enabled", true)) {
                    continue;
                }
                String name = section.getString("recipe-book.name", id);
                String iconId = section.getString("icon", "PAPER");
                List<List<IngredientOption>> stages = parseStages(section.getConfigurationSection("stage"));
                List<ResultTier> results = parseResults(section.getList("result"));
                entries.add(new RecipeEntry(id, name, furnitureLabel, iconId, stages, results));
            }
        }
        return entries;
    }

    /** Groups {@link #load()}'s result by furniture label, preserving file-discovery order. */
    public static Map<String, List<RecipeEntry>> loadGroupedByFurniture() {
        Map<String, List<RecipeEntry>> grouped = new LinkedHashMap<>();
        for (RecipeEntry entry : load()) {
            grouped.computeIfAbsent(entry.furnitureLabel(), k -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private static List<List<IngredientOption>> parseStages(ConfigurationSection stageSection) {
        List<List<IngredientOption>> stages = new ArrayList<>();
        if (stageSection == null) {
            return stages;
        }
        List<String> keys = new ArrayList<>(stageSection.getKeys(false));
        keys.sort(Comparator.comparingInt(k -> {
            try {
                return Integer.parseInt(k);
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }));
        for (String key : keys) {
            Object value = stageSection.get(key);
            List<IngredientOption> options = new ArrayList<>();
            if (value instanceof List<?> list) {
                for (Object o : list) {
                    options.add(new IngredientOption(String.valueOf(o)));
                }
            } else if (value != null) {
                options.add(new IngredientOption(String.valueOf(value)));
            }
            if (!options.isEmpty()) {
                stages.add(options);
            }
        }
        return stages;
    }

    private static List<ResultTier> parseResults(List<?> resultList) {
        List<ResultTier> results = new ArrayList<>();
        if (resultList == null || resultList.isEmpty()) {
            return results;
        }
        // The 3-tier result is written as one folded YAML scalar: "id amount weight, id amount weight, ...".
        // Bonus lines like "exp:100 100" (only seen in the plugin's own test recipe) are skipped.
        String combined = String.valueOf(resultList.get(0));
        for (String part : combined.split(",")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            String[] tokens = part.split("\\s+");
            if (tokens[0].contains(":") && !tokens[0].startsWith("ia:")) {
                continue; // e.g. "exp:100" - not an item result
            }
            try {
                if (tokens.length >= 3) {
                    results.add(new ResultTier(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2])));
                } else {
                    results.add(new ResultTier(tokens[0], 1, 100));
                }
            } catch (NumberFormatException ignored) {
                // malformed entry, skip
            }
        }
        return results;
    }

    /** Resolves an AddCook/vanilla item id (e.g. {@code "ia:addcook:addcook_food_x"} or {@code "CARROT"}) into a real icon. */
    public static ItemStack resolveIcon(String id) {
        if (id.startsWith("ia:")) {
            String iaId = id.substring("ia:".length());
            if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                CustomStack custom = CustomStack.getInstance(iaId);
                if (custom != null) {
                    return custom.getItemStack();
                }
            }
            return new ItemStack(Material.PAPER);
        }
        try {
            return new ItemStack(Material.valueOf(id));
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.PAPER);
        }
    }
}
