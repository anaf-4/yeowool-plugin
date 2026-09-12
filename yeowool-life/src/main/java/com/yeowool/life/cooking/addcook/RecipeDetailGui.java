package com.yeowool.life.cooking.addcook;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows one {@link AddCookRecipeIndex.RecipeEntry}'s ingredients (top section) and possible
 * results (bottom section) — opened by clicking a recipe in {@link MyRecipesGui}.
 */
public final class RecipeDetailGui extends YeowoolGui {

    private static final int[] INGREDIENT_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] RESULT_SLOTS = {19, 20, 21};
    private static final int SLOT_INGREDIENT_LABEL = 0;
    private static final int SLOT_RESULT_LABEL = 18;
    private static final int SLOT_BACK = 31;
    private static final String[] TIER_NAMES = {"일반 등급", "은별 등급", "금별 등급"};

    public RecipeDetailGui(Player viewer, AddCookRecipeIndex.RecipeEntry recipe, Runnable onBack) {
        super(36, Component.text("레시피: ", NamedTextColor.GOLD)
                .append(MiniMessage.miniMessage().deserialize(recipe.displayName())).decoration(TextDecoration.ITALIC, false));

        setButton(SLOT_INGREDIENT_LABEL, GuiButton.display(labelItem("필요한 재료", NamedTextColor.AQUA)));
        int slotIndex = 0;
        for (List<AddCookRecipeIndex.IngredientOption> stage : recipe.stages()) {
            for (AddCookRecipeIndex.IngredientOption option : stage) {
                if (slotIndex >= INGREDIENT_SLOTS.length) {
                    break;
                }
                ItemStack icon = AddCookRecipeIndex.resolveIcon(option.itemId());
                List<Component> lore = new ArrayList<>();
                if (stage.size() > 1) {
                    lore.add(Component.text("이 중 하나만 있으면 됩니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                setButton(INGREDIENT_SLOTS[slotIndex], GuiButton.display(withLore(icon, lore)));
                slotIndex++;
            }
        }

        setButton(SLOT_RESULT_LABEL, GuiButton.display(labelItem("완성품", NamedTextColor.GREEN)));
        List<AddCookRecipeIndex.ResultTier> results = recipe.results();
        boolean tiered = results.size() > 1;
        for (int i = 0; i < results.size() && i < RESULT_SLOTS.length; i++) {
            AddCookRecipeIndex.ResultTier tier = results.get(i);
            ItemStack icon = AddCookRecipeIndex.resolveIcon(tier.itemId());
            List<Component> lore = new ArrayList<>();
            if (tiered && i < TIER_NAMES.length) {
                lore.add(Component.text(TIER_NAMES[i], NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("수량: " + tier.amount() + "개", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("확률: " + tier.weight() + "/" + totalWeight(results), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            setButton(RESULT_SLOTS[i], GuiButton.display(withLore(icon, lore)));
        }

        setButton(SLOT_BACK, GuiButton.of(navItem("뒤로가기"), event -> onBack.run()));
    }

    private static int totalWeight(List<AddCookRecipeIndex.ResultTier> results) {
        int total = 0;
        for (AddCookRecipeIndex.ResultTier tier : results) {
            total += tier.weight();
        }
        return total;
    }

    private ItemStack withLore(ItemStack stack, List<Component> extraLore) {
        if (extraLore.isEmpty()) {
            return stack;
        }
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(extraLore);
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private ItemStack labelItem(String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
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
