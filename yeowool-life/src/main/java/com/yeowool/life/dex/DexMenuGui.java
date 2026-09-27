package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.life.fishing.FishCatalogGui;
import com.yeowool.life.fishing.FishRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * {@code /도감} — a hub screen picking between the four collection
 * categories, so the fishing catalog (which predates this) and the newer
 * 광물/사냥/작물 catalogs all live under one discoverable entry point.
 */
public final class DexMenuGui extends YeowoolGui {

    public DexMenuGui(YeowoolCoreAPI core, List<FishRarity> fishRarities, List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming,
                      int fishBackgroundOffsetPx, Map<String, DexRewardService.Progress> progress) {
        super(27, Component.text("여울 도감", NamedTextColor.DARK_AQUA));

        setButton(11, GuiButton.of(categoryIcon(Material.TROPICAL_FISH, "물고기 도감", progress.get("fishing")), event ->
                new FishCatalogGui(core, (Player) event.getWhoClicked(), fishRarities, 0, fishBackgroundOffsetPx).open((Player) event.getWhoClicked())));
        setButton(12, GuiButton.of(categoryIcon(Material.DIAMOND_ORE, "광물 도감", progress.get("mining")), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "광물 도감", "dex.mining.", mining).open((Player) event.getWhoClicked())));
        setButton(14, GuiButton.of(categoryIcon(Material.ZOMBIE_HEAD, "사냥 도감", progress.get("hunting")), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "사냥 도감", "dex.hunting.", hunting).open((Player) event.getWhoClicked())));
        setButton(15, GuiButton.of(categoryIcon(Material.WHEAT, "작물 도감", progress.get("farming")), event ->
                new DexCatalogGui(core, (Player) event.getWhoClicked(), "작물 도감", "dex.farming.", farming).open((Player) event.getWhoClicked())));
    }

    /** {@code progress} is null when rewards are off or the viewer's data isn't loaded — then the icon has no lore. */
    private ItemStack categoryIcon(Material material, String name, DexRewardService.Progress progress) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        if (progress != null && progress.total() == 0) {
            meta.lore(List.of(Component.text("등록된 항목 없음", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        } else if (progress != null) {
            meta.lore(List.of(
                    Component.text("수집 " + progress.owned() + "/" + progress.total() + " (" + progress.percent() + "%)", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    (progress.nextMilestone() < 0
                            ? Component.text("모든 보상 획득", NamedTextColor.GREEN)
                            : Component.text("다음 보상(" + progress.nextMilestone() + "%)까지 " + progress.remaining() + "종", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false)));
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
