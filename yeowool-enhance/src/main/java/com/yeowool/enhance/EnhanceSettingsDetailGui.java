package com.yeowool.enhance;

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
 * One level's {@code /강화설정} screen — 필요 온/재료 for the +N강 -> +(N+1)강
 * step, each editable via {@link EnhanceSettingsAnvilListener}. Prev/next
 * jump straight to the neighboring level so an admin can tune the whole
 * curve without bouncing back to {@link EnhanceSettingsGui} every time.
 */
public final class EnhanceSettingsDetailGui extends YeowoolGui {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_CURRENCY = 11;
    private static final int SLOT_MATERIAL = 15;
    private static final int SLOT_PREV = 27;
    private static final int SLOT_BACK = 29;
    private static final int SLOT_CLOSE = 31;
    private static final int SLOT_NEXT = 33;

    public EnhanceSettingsDetailGui(EnhanceService service, EnhanceCostManager costs, EnhanceSettingsAnvilListener anvil, int level) {
        super(36, Component.text("⚒ +" + level + "강 설정", NamedTextColor.GOLD));

        EnhanceConfig config = service.config();
        EnhanceTier tier = config.tierFor(level);
        var cost = costs.costFor(level);

        setButton(SLOT_INFO, GuiButton.display(infoIcon(config, tier, level)));
        setButton(SLOT_CURRENCY, GuiButton.of(currencyIcon(cost.currency()), event -> {
            if (event.getWhoClicked() instanceof Player admin) {
                anvil.beginCurrencyEdit(admin, level);
            }
        }));
        setButton(SLOT_MATERIAL, GuiButton.of(materialIcon(cost.materialId(), cost.materialAmount()), event -> {
            if (event.getWhoClicked() instanceof Player admin) {
                ItemStack hand = admin.getInventory().getItemInMainHand();
                String materialId = hand.getType().isAir() ? cost.materialId() : resolveId(hand);
                anvil.beginMaterialAmountEdit(admin, level, materialId);
            }
        }));

        if (level > 0) {
            setButton(SLOT_PREV, GuiButton.of(navItem("<- +" + (level - 1) + "강"), event ->
                    new EnhanceSettingsDetailGui(service, costs, anvil, level - 1).open((Player) event.getWhoClicked())));
        }
        setButton(SLOT_BACK, GuiButton.of(navItem("목록으로"), event ->
                new EnhanceSettingsGui(service, costs, anvil, level / EnhanceSettingsGui.PAGE_SIZE).open((Player) event.getWhoClicked())));
        setButton(SLOT_CLOSE, GuiButton.of(EnhanceIcons.closeIcon(), event -> event.getWhoClicked().closeInventory()));
        if (level + 1 < config.maxLevel()) {
            setButton(SLOT_NEXT, GuiButton.of(navItem("+" + (level + 1) + "강 ->"), event ->
                    new EnhanceSettingsDetailGui(service, costs, anvil, level + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack infoIcon(EnhanceConfig config, EnhanceTier tier, int level) {
        ItemStack stack = new ItemStack(Material.ANVIL);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("+" + level + "강 -> +" + (level + 1) + "강", tier.color(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("등급: " + tier.name(), tier.color()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("성공 확률: " + config.successRate(level) + "%", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        if (config.isFailRisky(level)) {
            lore.add(Component.text("실패 시 하락 " + config.failDowngradeChancePercent() + "% / 파괴 " + config.failDestroyChancePercent() + "%",
                    NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        if (config.crossesTierAt(level)) {
            EnhanceTier next = config.tierFor(level + 1);
            lore.add(Component.text("성공 시 " + next.name() + " 등급으로 승급", next.color()).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack currencyIcon(long currency) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("필요 온: " + String.format("%,d", currency), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭하여 값 변경", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack materialIcon(String materialId, int amount) {
        ItemStack stack = resolveIcon(materialId);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("필요 재료: " + EnhanceMaterialResolver.displayName(materialId) + " x" + amount + "개",
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("빈손 클릭: 개수만 변경", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("아이템을 들고 클릭: 재료+개수 변경", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
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

    private ItemStack resolveIcon(String materialId) {
        if (EnhanceMaterialResolver.isCustomItem(materialId) && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(materialId);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        try {
            return new ItemStack(Material.valueOf(materialId));
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.BARRIER);
        }
    }

    private String resolveId(ItemStack hand) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.byItemStack(hand);
            if (custom != null) {
                return custom.getNamespacedID();
            }
        }
        return hand.getType().name();
    }
}
