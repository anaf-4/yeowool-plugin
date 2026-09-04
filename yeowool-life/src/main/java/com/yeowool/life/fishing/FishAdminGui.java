package com.yeowool.life.fishing;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
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
 * {@code /낚시관리 물고기} — 관리진 전용, {@code config.yml}에 등록된 모든 물고기 종을
 * 등급 상관없이 전부 보여주고 클릭하면 그 자리에서 인벤토리로 지급한다. 레이아웃/배경은
 * {@link FishCatalogGui}(플레이어용 도감, 미포획은 "???"로 가려짐)와 동일하지만, 여기는
 * 전부 공개돼있고 클릭 시 실제로 아이템을 준다는 점만 다르다.
 */
public final class FishAdminGui extends YeowoolGui {

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

    public FishAdminGui(List<FishRarity> rarities, int page, int backgroundOffsetPx) {
        super(54, FishBackgroundImages.title(backgroundOffsetPx, "fish_codex",
                Component.text("물고기 지급 (페이지 " + (page + 1) + ")", NamedTextColor.AQUA)));

        List<Entry> entries = flatten(rarities);
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            Entry entry = entries.get(i);
            setButton(DISPLAY_SLOTS[i - from], GuiButton.of(buildIcon(entry.rarity(), entry.species()), event -> {
                if (event.getWhoClicked() instanceof Player admin) {
                    var leftover = admin.getInventory().addItem(buildIcon(entry.rarity(), entry.species()));
                    leftover.values().forEach(item -> admin.getWorld().dropItemNaturally(admin.getLocation(), item));
                    admin.sendMessage(Component.text(entry.species().name() + "을(를) 지급했습니다.", NamedTextColor.GREEN));
                }
            }));
        }

        if (page > 0) {
            for (int slot : SLOTS_PREV) {
                setButton(slot, GuiButton.of(navItem("이전 페이지"), event ->
                        new FishAdminGui(rarities, page - 1, backgroundOffsetPx).open((Player) event.getWhoClicked())));
            }
        }
        setButton(SLOT_CLOSE, GuiButton.of(navItem("닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < entries.size()) {
            for (int slot : SLOTS_NEXT) {
                setButton(slot, GuiButton.of(navItem("다음 페이지"), event ->
                        new FishAdminGui(rarities, page + 1, backgroundOffsetPx).open((Player) event.getWhoClicked())));
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

    private ItemStack buildIcon(FishRarity rarity, FishSpecies species) {
        ItemStack stack = resolveIcon(species);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(species.name(), rarity.color()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("등급: " + rarity.name(), rarity.color()).decoration(TextDecoration.ITALIC, false));
        if (!species.description().isBlank()) {
            lore.add(Component.text(species.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("클릭하여 지급받기", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
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
