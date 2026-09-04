package com.yeowool.market.npcshop;

import com.yeowool.core.api.model.CurrencyType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code config.yml}'s {@code npc-shop.shops} section into
 * {@link ShopDefinition}s, and {@code npc-shop.citizens-npc-shops} into an
 * NPC-id-to-shop-id map for {@link com.yeowool.market.citizens.CitizensShopListener}.
 */
public final class ShopConfigLoader {

    private ShopConfigLoader() {
    }

    public static Map<String, ShopDefinition> loadShops(JavaPlugin plugin) {
        Map<String, ShopDefinition> shops = new LinkedHashMap<>();
        ConfigurationSection shopsSection = plugin.getConfig().getConfigurationSection("npc-shop.shops");
        if (shopsSection == null) {
            plugin.getLogger().warning("npc-shop.shops 설정이 없습니다.");
            return shops;
        }

        for (String shopId : shopsSection.getKeys(false)) {
            ConfigurationSection section = shopsSection.getConfigurationSection(shopId);
            if (section == null) {
                continue;
            }
            try {
                shops.put(shopId, parseShop(plugin, shopId, section));
            } catch (Exception e) {
                plugin.getLogger().warning("npc-shop.shops." + shopId + " 설정이 잘못되었습니다: " + e.getMessage());
            }
        }
        return shops;
    }

    private static ShopDefinition parseShop(JavaPlugin plugin, String shopId, ConfigurationSection section) {
        String title = section.getString("title", shopId);
        int size = normalizeSize(section.getInt("size", 54));
        ShopMode mode = ShopMode.valueOf(section.getString("mode", "BOTH").toUpperCase());

        ShopDefinition.Decoration decoration = null;
        ConfigurationSection decorationSection = section.getConfigurationSection("decoration");
        if (decorationSection != null) {
            Material material = Material.valueOf(decorationSection.getString("material", "GRAY_STAINED_GLASS_PANE"));
            int customModelData = decorationSection.getInt("custom-model-data", -1);
            decoration = new ShopDefinition.Decoration(material, customModelData);
        }

        List<ShopItem> items = parseItems(plugin, shopId, "items", section.getMapList("items"), size);

        List<ShopItem> rotationPool = List.of();
        List<Integer> rotationSlots = List.of();
        int rotationIntervalMinutes = 0;
        ConfigurationSection rotationSection = section.getConfigurationSection("rotation");
        if (rotationSection != null) {
            rotationPool = parseItems(plugin, shopId, "rotation.pool", rotationSection.getMapList("pool"), size);
            rotationSlots = rotationSection.getIntegerList("slots");
            rotationIntervalMinutes = rotationSection.getInt("interval-minutes", 0);
        }

        int pageCount = 1 + items.stream().mapToInt(ShopItem::page).max().orElse(0);
        return new ShopDefinition(shopId, title, size, mode, items, decoration, rotationPool, rotationSlots, rotationIntervalMinutes, pageCount);
    }

    private static List<ShopItem> parseItems(JavaPlugin plugin, String shopId, String fieldName, List<Map<?, ?>> rawItems, int size) {
        List<ShopItem> items = new ArrayList<>();
        int autoSlot = 0;
        for (Map<?, ?> entry : rawItems) {
            try {
                // Exactly one of "item-id" (ItemsAdder namespaced id) or "material" (vanilla) must be set.
                String itemId = entry.containsKey("item-id") ? entry.get("item-id").toString() : null;
                Material material = itemId == null ? Material.valueOf(entry.get("material").toString()) : null;

                long buy = entry.containsKey("buy-price") ? ((Number) entry.get("buy-price")).longValue() : 0L;
                long sell = entry.containsKey("sell-price") ? ((Number) entry.get("sell-price")).longValue() : 0L;
                int customModelData = entry.containsKey("custom-model-data") ? ((Number) entry.get("custom-model-data")).intValue() : -1;
                int slot = entry.containsKey("slot") ? ((Number) entry.get("slot")).intValue() : nextFreeSlot(items, autoSlot, size);
                autoSlot = slot + 1;
                CurrencyType currency = entry.containsKey("currency")
                        ? CurrencyType.valueOf(entry.get("currency").toString().toUpperCase())
                        : CurrencyType.ON;
                // 1-indexed in YAML (matches the "Spectra ShopGUI+" reference shop files' "page:" key), 0-indexed internally.
                int page = entry.containsKey("page") ? Math.max(0, ((Number) entry.get("page")).intValue() - 1) : 0;
                boolean strictMatch = entry.containsKey("strict-match") && Boolean.parseBoolean(entry.get("strict-match").toString());
                items.add(new ShopItem(material, itemId, buy, sell, slot, customModelData, currency, page, strictMatch, null));
            } catch (Exception e) {
                plugin.getLogger().warning("npc-shop.shops." + shopId + "." + fieldName + " 항목이 잘못되었습니다: " + entry);
            }
        }
        return items;
    }

    private static int nextFreeSlot(List<ShopItem> placedSoFar, int fromSlot, int size) {
        return Math.min(fromSlot, size - 1);
    }

    private static int normalizeSize(int requested) {
        int rows = Math.max(1, Math.min(6, (requested + 8) / 9));
        return rows * 9;
    }

    public static Map<Integer, String> loadCitizensNpcShops(JavaPlugin plugin) {
        Map<Integer, String> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("npc-shop.citizens-npc-shops");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                result.put(Integer.parseInt(key), section.getString(key));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("npc-shop.citizens-npc-shops 키는 NPC ID(숫자)여야 합니다: " + key);
            }
        }
        return result;
    }
}
