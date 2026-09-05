package com.yeowool.life.fishing;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.life.fishing.customfishing.CustomFishingBridge;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /도감} — every fish species across all rarities, showing how many
 * of each the viewer has caught. Uncaught species show as a "???"
 * silhouette so the catalog also works as a discovery checklist.
 *
 * <p>Fish only ever go in {@link #DISPLAY_SLOTS} (the "aquarium glass" area
 * of the {@code fish_codex} background — 3 rows of 7, slots 10-16/19-25/28-34)
 * so nothing overlaps the background's own frame artwork; every other slot
 * is left completely empty. Paginated the same way {@code FriendListGui} is,
 * just with a much smaller page size (21, not 45) since only those 21 slots
 * are usable — the "fishing_expansion" roster (60 species) still needs it.
 * Nav buttons (38-39 이전 페이지, 40 닫기, 41-42 다음 페이지) use the pack's
 * own invisible item so only the background's baked-in button art shows.
 */
public final class FishCatalogGui extends YeowoolGui {

    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int PAGE_SIZE = DISPLAY_SLOTS.length;
    private static final int[] SLOTS_PREV = {38, 39};
    private static final int SLOT_CLOSE = 40;
    private static final int[] SLOTS_NEXT = {41, 42};
    private static final String INVISIBLE_ICON_ID = "fishing_expansion:invisible_item";

    public FishCatalogGui(YeowoolCoreAPI core, Player viewer, List<FishRarity> rarities, int page, int backgroundOffsetPx) {
        super(54, FishBackgroundImages.title(backgroundOffsetPx, "fish_codex",
                Component.text("낚시 도감 (페이지 " + (page + 1) + ")", NamedTextColor.AQUA)));

        List<Entry> entries = flatten(rarities);
        // getIfLoaded (not getOnline) — a transient join/data-load timing gap must
        // never crash this GUI's constructor; worst case every fish just renders
        // as uncaught instead of throwing.
        var data = core.playerData().getIfLoaded(viewer.getUniqueId());
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            Entry entry = entries.get(i);
            long caught = data.map(d -> d.getStatistic(entry.species().statisticKey())).orElse(0L);
            long bestSizeMm = data.map(d -> d.getStatistic(entry.species().sizeRecordStatisticKey())).orElse(0L);
            setButton(DISPLAY_SLOTS[i - from], GuiButton.display(buildIcon(viewer, entry.rarity(), entry.species(), caught, bestSizeMm)));
        }

        if (page > 0) {
            for (int slot : SLOTS_PREV) {
                setButton(slot, GuiButton.of(navItem("이전 페이지"), event ->
                        new FishCatalogGui(core, (Player) event.getWhoClicked(), rarities, page - 1, backgroundOffsetPx).open((Player) event.getWhoClicked())));
            }
        }
        setButton(SLOT_CLOSE, GuiButton.of(navItem("닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < entries.size()) {
            for (int slot : SLOTS_NEXT) {
                setButton(slot, GuiButton.of(navItem("다음 페이지"), event ->
                        new FishCatalogGui(core, (Player) event.getWhoClicked(), rarities, page + 1, backgroundOffsetPx).open((Player) event.getWhoClicked())));
            }
        }
    }

    private record Entry(FishRarity rarity, FishSpecies species) {
    }

    private static List<Entry> flatten(List<FishRarity> rarities) {
        List<Entry> entries = new ArrayList<>();
        for (FishRarity rarity : rarities) {
            for (FishSpecies species : rarity.species()) {
                entries.add(new Entry(rarity, species));
            }
        }
        return entries;
    }

    /**
     * The pack's own {@code fishing_expansion:invisible_item} (same one used by its
     * DeluxeMenus fish_codex nav row) — renders blank so only the background's baked-in
     * button art at slots 38-42 shows, while still being a real hoverable/clickable item.
     * Falls back to a plain glass pane (same fallback style as {@code AmountSelectionGui}'s
     * {@code shop_empty_slot} nav buttons) if ItemsAdder isn't present.
     */
    private ItemStack navItem(String name) {
        ItemStack stack = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(INVISIBLE_ICON_ID);
            if (custom != null) {
                stack = custom.getItemStack();
            }
        }
        if (stack == null) {
            stack = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(Player viewer, FishRarity rarity, FishSpecies species, long caught, long bestSizeMm) {
        boolean known = caught > 0;
        if (known && species.customFishingId() != null && CustomFishingBridge.isEnabled()) {
            ItemStack cfStack = CustomFishingBridge.buildItem(viewer, species.customFishingId());
            ItemMeta cfMeta = cfStack.getItemMeta();
            List<Component> cfLore = cfMeta.hasLore() ? new ArrayList<>(cfMeta.lore()) : new ArrayList<>();
            cfLore.add(Component.text("등급: " + rarity.name(), rarity.color()));
            cfLore.add(Component.text("포획 수: " + caught + "마리", NamedTextColor.GRAY));
            cfMeta.lore(cfLore);
            cfStack.setItemMeta(cfMeta);
            return cfStack;
        }
        ItemStack stack = known ? resolveIcon(species) : new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(known
                ? Component.text(species.name(), rarity.color())
                : Component.text("???", NamedTextColor.DARK_GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("등급: " + rarity.name(), rarity.color()));
        if (known) {
            if (!species.description().isBlank()) {
                lore.add(Component.text(species.description(), NamedTextColor.GRAY));
            }
            lore.add(Component.text("포획 수: " + caught + "마리", NamedTextColor.GRAY));
            lore.add(Component.text("최고 기록: " + String.format("%.1f", bestSizeMm / 10.0) + "cm", NamedTextColor.AQUA));
        } else {
            lore.add(Component.text("아직 잡지 못했습니다.", NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack resolveIcon(FishSpecies species) {
        if (species.customIconId() != null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(species.customIconId());
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(species.material());
    }
}
