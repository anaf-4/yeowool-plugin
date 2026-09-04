package com.yeowool.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Static balance settings for {@code /인챈트강화}, loaded from {@code config.yml}'s {@code inchant} section. */
public final class EnchantConfig {

    public record TierSetting(long cost, int dustMin, int dustMax, int levelMin, int levelMax) {
    }

    private final Map<EnchantTier, TierSetting> tiers;
    private final List<Enchantment> enchantPool;
    private final double dismantleRefundPercent;
    private final int backgroundOffsetPx;

    private EnchantConfig(Map<EnchantTier, TierSetting> tiers, List<Enchantment> enchantPool,
                           double dismantleRefundPercent, int backgroundOffsetPx) {
        this.tiers = tiers;
        this.enchantPool = enchantPool;
        this.dismantleRefundPercent = dismantleRefundPercent;
        this.backgroundOffsetPx = backgroundOffsetPx;
    }

    public static EnchantConfig load(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("inchant");
        if (root == null) {
            throw new IllegalStateException("config.yml에 inchant 섹션이 없습니다.");
        }

        Map<EnchantTier, TierSetting> tiers = new EnumMap<>(EnchantTier.class);
        for (Map<?, ?> raw : root.getMapList("tiers")) {
            try {
                EnchantTier tier = EnchantTier.valueOf(String.valueOf(raw.get("name")).toUpperCase(Locale.ROOT));
                tiers.put(tier, new TierSetting(
                        ((Number) raw.get("cost")).longValue(),
                        ((Number) raw.get("dust-min")).intValue(),
                        ((Number) raw.get("dust-max")).intValue(),
                        ((Number) raw.get("level-min")).intValue(),
                        ((Number) raw.get("level-max")).intValue()));
            } catch (Exception ignored) {
                // skip malformed entry
            }
        }
        for (EnchantTier tier : EnchantTier.values()) {
            tiers.putIfAbsent(tier, new TierSetting(1000, 1, 5, 1, 2));
        }

        List<Enchantment> enchantPool = parseEnchantments(root.getStringList("enchant-pool"));

        return new EnchantConfig(tiers, enchantPool,
                root.getDouble("dismantle-refund-percent", 30.0),
                root.getInt("gui-background-offset", -32));
    }

    private static List<Enchantment> parseEnchantments(List<String> raw) {
        List<Enchantment> result = new ArrayList<>();
        for (String id : raw) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT)));
            if (enchantment != null) {
                result.add(enchantment);
            }
        }
        return result;
    }

    public TierSetting settingFor(EnchantTier tier) {
        return tiers.get(tier);
    }

    /**
     * All enchantments a purchased book might roll — vanilla's own anvil
     * item-compatibility check (e.g. can't put PROTECTION on a sword) gates
     * which gear each one can actually be combined onto, so this stays one
     * shared pool rather than a separate weapon/armor split.
     */
    public List<Enchantment> enchantPool() {
        return enchantPool;
    }

    public double dismantleRefundPercent() {
        return dismantleRefundPercent;
    }

    public int backgroundOffsetPx() {
        return backgroundOffsetPx;
    }
}
