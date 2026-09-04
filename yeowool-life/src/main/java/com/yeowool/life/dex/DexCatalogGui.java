package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Generic flat {@code /도감} category screen for 광물/사냥/작물 — same
 * "???" silhouette discovery-checklist idea as {@link com.yeowool.life.fishing.FishCatalogGui},
 * just without fishing's rarity-tier grouping since ore/mob/crop entries
 * aren't weighted-random rolls.
 */
public final class DexCatalogGui extends YeowoolGui {

    public DexCatalogGui(YeowoolCoreAPI core, Player viewer, String title, String statPrefix, List<DexEntry> entries) {
        super(Math.max(9, ((entries.size() / 9) + 1) * 9), Component.text(title, NamedTextColor.AQUA));

        // getIfLoaded (not getOnline) — a transient join/data-load timing gap must
        // never crash this GUI's constructor; worst case every entry just renders
        // as undiscovered instead of throwing.
        var data = core.playerData().getIfLoaded(viewer.getUniqueId());
        int slot = 0;
        for (DexEntry entry : entries) {
            long count = data.map(d -> d.getStatistic(statPrefix + entry.id())).orElse(0L);
            setButton(slot++, GuiButton.display(buildIcon(entry, count)));
        }
    }

    private ItemStack buildIcon(DexEntry entry, long count) {
        boolean known = count > 0;
        ItemStack stack = new ItemStack(known ? entry.icon() : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(known
                ? Component.text(entry.display(), NamedTextColor.GOLD)
                : Component.text("???", NamedTextColor.DARK_GRAY));
        meta.lore(List.of(known
                ? Component.text("획득 수: " + count + "개", NamedTextColor.GRAY)
                : Component.text("아직 발견하지 못했습니다.", NamedTextColor.DARK_GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }
}
