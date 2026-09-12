package com.yeowool.life.scrapyard;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Loads {@code scrapyard.*} from config.yml — timing, weight tiers, monster ids, and the scrap item catalog. */
public final class ScrapyardConfig {

    private final boolean enabled;
    private final int sessionDurationSeconds;
    private final int chestEmptyChancePercent;
    private final TreeMap<Integer, Integer> weightTiers;
    private final List<String> mobIds;
    private final int mobSpawnIntervalSeconds;
    private final int mobMaxAlive;
    private final Map<String, ScrapItem> scrapItems;
    private final String bossMobId;
    private final int bossSpawnDelaySeconds;

    private ScrapyardConfig(boolean enabled, int sessionDurationSeconds, int chestEmptyChancePercent,
                             TreeMap<Integer, Integer> weightTiers, List<String> mobIds, int mobSpawnIntervalSeconds,
                             int mobMaxAlive, Map<String, ScrapItem> scrapItems, String bossMobId, int bossSpawnDelaySeconds) {
        this.enabled = enabled;
        this.sessionDurationSeconds = sessionDurationSeconds;
        this.chestEmptyChancePercent = chestEmptyChancePercent;
        this.weightTiers = weightTiers;
        this.mobIds = mobIds;
        this.mobSpawnIntervalSeconds = mobSpawnIntervalSeconds;
        this.mobMaxAlive = mobMaxAlive;
        this.scrapItems = scrapItems;
        this.bossMobId = bossMobId;
        this.bossSpawnDelaySeconds = bossSpawnDelaySeconds;
    }

    public static ScrapyardConfig load(FileConfiguration config) {
        boolean enabled = config.getBoolean("scrapyard.enabled", true);
        int sessionDurationSeconds = config.getInt("scrapyard.session-duration-seconds", 600);
        int chestEmptyChancePercent = config.getInt("scrapyard.chest-empty-chance", 30);

        TreeMap<Integer, Integer> weightTiers = new TreeMap<>();
        ConfigurationSection tierSection = config.getConfigurationSection("scrapyard.weight-tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                try {
                    weightTiers.put(Integer.parseInt(key), tierSection.getInt(key));
                } catch (NumberFormatException ignored) {
                    // 숫자가 아닌 키는 무시
                }
            }
        }

        List<String> mobIds = config.getStringList("scrapyard.mob.ids");
        int mobSpawnIntervalSeconds = config.getInt("scrapyard.mob.spawn-interval-seconds", 30);
        int mobMaxAlive = config.getInt("scrapyard.mob.max-alive", 10);

        String bossMobId = config.getString("scrapyard.boss.mob-id", "");
        int bossSpawnDelaySeconds = config.getInt("scrapyard.boss.spawn-delay-seconds", 60);

        Map<String, ScrapItem> scrapItems = new LinkedHashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("scrapyard.scrap-items");
        if (itemsSection != null) {
            for (String id : itemsSection.getKeys(false)) {
                ConfigurationSection item = itemsSection.getConfigurationSection(id);
                if (item == null) {
                    continue;
                }
                scrapItems.put(id, new ScrapItem(
                        id,
                        item.getString("material", "PAPER"),
                        item.getString("name", id),
                        item.getInt("weight", 1),
                        item.getInt("loot-weight", 10)));
            }
        }

        return new ScrapyardConfig(enabled, sessionDurationSeconds, chestEmptyChancePercent, weightTiers,
                mobIds, mobSpawnIntervalSeconds, mobMaxAlive, scrapItems, bossMobId, bossSpawnDelaySeconds);
    }

    public boolean enabled() {
        return enabled;
    }

    public int sessionDurationSeconds() {
        return sessionDurationSeconds;
    }

    public int chestEmptyChancePercent() {
        return chestEmptyChancePercent;
    }

    public List<String> mobIds() {
        return mobIds;
    }

    public int mobSpawnIntervalSeconds() {
        return mobSpawnIntervalSeconds;
    }

    public int mobMaxAlive() {
        return mobMaxAlive;
    }

    public Map<String, ScrapItem> scrapItems() {
        return scrapItems;
    }

    /** Empty string if no boss is configured. */
    public String bossMobId() {
        return bossMobId;
    }

    public boolean hasBoss() {
        return !bossMobId.isBlank();
    }

    public int bossSpawnDelaySeconds() {
        return bossSpawnDelaySeconds;
    }

    /** 0 (no slowness) if under every configured tier's threshold, otherwise the highest tier's amplifier that {@code totalWeight} has reached. */
    public int slownessAmplifierFor(int totalWeight) {
        var floor = weightTiers.floorEntry(totalWeight);
        return floor == null ? 0 : floor.getValue();
    }

    public static NamespacedKey itemPdcKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "scrapyard_item");
    }

    /** Builds an actual {@link ItemStack} for {@code item}, tagged with its id so weight-calc/death-clearing can identify it reliably regardless of display name. */
    public ItemStack build(ScrapItem item, JavaPlugin plugin) {
        ItemStack stack = resolveBase(item.materialOrItemId());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(item.displayName()).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(itemPdcKey(plugin), PersistentDataType.STRING, item.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack resolveBase(String materialOrItemId) {
        if (materialOrItemId.startsWith("ia:") && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(materialOrItemId.substring(3));
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        try {
            return new ItemStack(Material.valueOf(materialOrItemId));
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.PAPER);
        }
    }
}
