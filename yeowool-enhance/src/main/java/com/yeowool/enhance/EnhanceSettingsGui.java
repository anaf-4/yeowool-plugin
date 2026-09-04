package com.yeowool.enhance;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /강화설정} — paginated list of every enhance level (0강 -> maxLevel-1강),
 * one click away from {@link EnhanceSettingsDetailGui} for that level. Same
 * 21-slot grid + prev/close/next nav row {@code FishAdminGui} uses.
 */
public final class EnhanceSettingsGui extends YeowoolGui {

    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    static final int PAGE_SIZE = DISPLAY_SLOTS.length;
    private static final int[] SLOTS_PREV = {38, 39};
    private static final int SLOT_CLOSE = 40;
    private static final int[] SLOTS_NEXT = {41, 42};

    public EnhanceSettingsGui(EnhanceService service, EnhanceCostManager costs, EnhanceSettingsAnvilListener anvil, int page) {
        super(54, Component.text("⚒ 강화 설정 (페이지 " + (page + 1) + ")", NamedTextColor.GOLD));

        int maxLevel = service.config().maxLevel();
        int from = page * PAGE_SIZE;
        int to = Math.min(maxLevel, from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            int level = i;
            setButton(DISPLAY_SLOTS[level - from], GuiButton.of(levelIcon(service, costs, level), event -> {
                if (event.getWhoClicked() instanceof Player admin) {
                    new EnhanceSettingsDetailGui(service, costs, anvil, level).open(admin);
                }
            }));
        }

        if (page > 0) {
            for (int slot : SLOTS_PREV) {
                setButton(slot, GuiButton.of(navItem("이전 페이지"), event ->
                        new EnhanceSettingsGui(service, costs, anvil, page - 1).open((Player) event.getWhoClicked())));
            }
        }
        setButton(SLOT_CLOSE, GuiButton.of(EnhanceIcons.closeIcon(), event -> event.getWhoClicked().closeInventory()));
        if (to < maxLevel) {
            for (int slot : SLOTS_NEXT) {
                setButton(slot, GuiButton.of(navItem("다음 페이지"), event ->
                        new EnhanceSettingsGui(service, costs, anvil, page + 1).open((Player) event.getWhoClicked())));
            }
        }
    }

    private ItemStack levelIcon(EnhanceService service, EnhanceCostManager costs, int level) {
        EnhanceTier tier = service.config().tierFor(level);
        var cost = costs.costFor(level);
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("+" + level + "강 -> +" + (level + 1) + "강", tier.color(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("등급: " + tier.name(), tier.color()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("성공 확률: " + service.config().successRate(level) + "%", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("필요 온: " + String.format("%,d", cost.currency()), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("필요 재료: " + EnhanceMaterialResolver.displayName(cost.materialId()) + " x" + cost.materialAmount() + "개",
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 설정 변경", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navItem(String name) {
        ItemStack stack = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
