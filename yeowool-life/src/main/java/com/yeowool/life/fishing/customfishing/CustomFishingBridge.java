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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /** One synthetic rarity gathering every CustomFishing item-type loot (rods/baits/utility items excluded — only {@link LootType#ITEM} loot). */
    public static FishRarity buildRarity() {
        List<FishSpecies> species = new ArrayList<>();
        for (Loot loot : BukkitCustomFishingPlugin.getInstance().getLootManager().getRegisteredLoots()) {
            if (loot.type() != LootType.ITEM) {
                continue;
            }
            // name/material/customIconId are unused for CustomFishing-backed
            // species — every renderer resolves the real item via buildItem().
            species.add(new FishSpecies(loot.id(), loot.id(), Material.PAPER, null, "", 0, 0, loot.id()));
        }
        species.sort(Comparator.comparing(FishSpecies::id));
        return new FishRarity("커스텀 낚시", 0, NamedTextColor.LIGHT_PURPLE, species);
    }

    /** Builds the real CustomFishing item for {@code lootId}, resolved with {@code viewer} as the placeholder context (name/lore included). */
    public static ItemStack buildItem(Player viewer, String lootId) {
        return BukkitCustomFishingPlugin.getInstance().getItemManager().buildAny(Context.player(viewer), lootId);
    }
}
