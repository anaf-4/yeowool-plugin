package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.life.fishing.FishCatalogGui;
import com.yeowool.life.fishing.FishRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /도감} — a hub screen picking between the four collection
 * categories, so the fishing catalog (which predates this) and the newer
 * 광물/사냥/작물 catalogs all live under one discoverable entry point.
 */
public final class DexMenuGui extends YeowoolGui {

    public DexMenuGui(YeowoolCoreAPI core, List<FishRarity> fishRarities, List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming, int fishBackgroundOffsetPx) {
        super(27, Component.text("여울 도감", NamedTextColor.DARK_AQUA));

        setButton(11, GuiButton.of(categoryIcon(Material.TROPICAL_FISH, "물고기 도감"), event ->
                new FishCatalogGui(core, (Player) event.getWhoClicked(), fishRarities, 0, fishBackgroundOffsetPx).open((Player) event.getWhoClicked())));
        setButton(12, GuiButton.of(categoryIcon(Material.DIAMOND_ORE, "광물 도감"), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "광물 도감", "dex.mining.", mining).open((Player) event.getWhoClicked())));
        setButton(14, GuiButton.of(categoryIcon(Material.ZOMBIE_HEAD, "사냥 도감"), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "사냥 도감", "dex.hunting.", hunting).open((Player) event.getWhoClicked())));
        setButton(15, GuiButton.of(categoryIcon(Material.WHEAT, "작물 도감"), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "작물 도감", "dex.farming.", farming).open((Player) event.getWhoClicked())));
    }

    private ItemStack categoryIcon(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        stack.setItemMeta(meta);
        return stack;
    }
}
