package com.yeowool.life.fishing.customfishing;

import com.yeowool.life.fishing.FishRarity;
import com.yeowool.life.fishing.FishSpecies;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.context.Context;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Bridges the CustomFishing plugin's own loot table into our 도감/물고기 지급
 * GUIs ({@link com.yeowool.life.fishing.FishCatalogGui}, {@link
 * com.yeowool.life.fishing.FishAdminGui}) so both roster sources show up
 * together, without CustomFishing loot ever being reachable from our own
 * {@link com.yeowool.life.fishing.FishingListener} roll (that stays on the
 * unmerged rarity list — these are two independent catch mechanisms).
 *
 * <p>A {@link FishSpecies} built here always carries a non-null {@link
 * FishSpecies#customFishingId()}; every icon/name resolution for such a
 * species should go through {@link #buildItem} instead of {@code material}/
 * {@code customIconId}, since the real registered item is the only reliable
 * source of its (often gradient-colored) display name.
 */
public final class CustomFishingBridge {

    private static final String PLUGIN_NAME = "CustomFishing";

    private CustomFishingBridge() {
    }

    public static boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    /** CustomFishing's own internal sentinel id meaning "defer to the vanilla fishing loot table" — has no material/item behind it, so building it resolves to an AIR stack (crashes anything that expects a real, giftable item). */
    private static final String VANILLA_SENTINEL_ID = "vanilla";

    /**
     * One synthetic rarity gathering every CustomFishing item-type loot (rods/baits/utility
     * items excluded — only {@link LootType#ITEM} loot). Excludes {@value #VANILLA_SENTINEL_ID}
     * (not a real item) and anything we ourselves exported via
     * {@link CustomFishingNativeFishExporter} (already shown through our own {@code
     * fishRarities} list — listing it again here would just duplicate it).
     */
    public static FishRarity buildRarity() {
        List<FishSpecies> species = new ArrayList<>();
        for (Loot loot : BukkitCustomFishingPlugin.getInstance().getLootManager().getRegisteredLoots()) {
            if (loot.type() != LootType.ITEM) {
                continue;
            }
            if (loot.id().equals(VANILLA_SENTINEL_ID) || CustomFishingNativeFishExporter.isExportedId(loot.id())) {
                continue;
            }
            // name/material/customIconId are unused for CustomFishing-backed
            // species — every renderer resolves the real item via buildItem().
            species.add(new FishSpecies(loot.id(), loot.id(), Material.PAPER, null, "", 0, 0, loot.id()));
        }
        species.sort(Comparator.comparing(FishSpecies::id));
        return new FishRarity("커스텀 낚시", 0, NamedTextColor.LIGHT_PURPLE, species);
    }

    /**
     * Builds the real CustomFishing item for {@code lootId}, resolved with {@code viewer} as
     * the placeholder context (name/lore included). Uses {@code buildInternal} — NOT {@code
     * buildAny}, which despite its name doesn't look up CustomFishing's own registered items at
     * all; it only resolves a bare vanilla material name or a {@code provider:id} string, so
     * calling it with one of our config keys (e.g. {@code "tuna_fish"}) throws or silently
     * returns a blank paper stack (see {@code BukkitItemManager.getOriginalStack}).
     */
    public static ItemStack buildItem(Player viewer, String lootId) {
        ItemStack stack = BukkitCustomFishingPlugin.getInstance().getItemManager().buildInternal(Context.player(viewer), lootId);
        // A registered entry with no resolvable material (CustomFishing's own "vanilla" sentinel
        // does this deliberately) builds down to a bare AIR stack, whose getItemMeta() is always
        // null — every caller here expects a real, meta-bearing item to slap lore onto, so swap
        // in a paper placeholder rather than letting that null propagate into an NPE.
        if (stack == null || stack.getType() == Material.AIR) {
            return new ItemStack(Material.PAPER);
        }
        return stack;
    }

    /**
     * One synthetic rarity gathering every custom rod id. Rods aren't {@link Loot}
     * (CustomFishing's {@code LootManager} only tracks fish/entity/block catches), so
     * there's no runtime registry to query them from — instead this reads the plugin's
     * own {@code contents/rod/*.yml} files directly and takes every lowercase top-level
     * key (the vanilla-item overrides in those files are always ALL_CAPS, e.g. {@code
     * FISHING_ROD}, so those are skipped).
     */
    public static FishRarity buildRodRarity() {
        return buildRarityFromContentFiles("낚싯대", "rod");
    }

    /** Same idea as {@link #buildRodRarity()} but for {@code contents/bait/*.yml}. */
    public static FishRarity buildBaitRarity() {
        return buildRarityFromContentFiles("미끼", "bait");
    }

    private static FishRarity buildRarityFromContentFiles(String label, String subfolder) {
        List<FishSpecies> species = new ArrayList<>();
        Plugin customFishing = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (customFishing != null) {
            File folder = new File(customFishing.getDataFolder(), "contents/" + subfolder);
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    for (String key : yaml.getKeys(false)) {
                        if (key.equals(key.toUpperCase(Locale.ROOT))) {
                            continue;
                        }
                        species.add(new FishSpecies(key, key, Material.PAPER, null, "", 0, 0, key));
                    }
                }
            }
        }
        species.sort(Comparator.comparing(FishSpecies::id));
        return new FishRarity(label, 0, NamedTextColor.LIGHT_PURPLE, species);
    }
}
