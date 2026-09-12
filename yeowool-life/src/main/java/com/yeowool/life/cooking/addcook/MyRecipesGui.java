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
 * {@code /레시피} — lists the recipes the player currently owns (holds the LuckPerms permission
 * {@code addcook.recipe.<id>} for, granted permanently the first time they use that recipe's
 * book — see {@link AddCookRecipeIndex}). Clicking one opens {@link RecipeDetailGui} showing its
 * ingredients and possible results.
 */
public final class MyRecipesGui extends YeowoolGui {

    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int PAGE_SIZE = DISPLAY_SLOTS.length;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    public MyRecipesGui(Player viewer, List<AddCookRecipeIndex.RecipeEntry> owned, int page) {
        super(54, Component.text("보유중인 레시피 (페이지 " + (page + 1) + ")", NamedTextColor.GOLD));

        int from = page * PAGE_SIZE;
        int to = Math.min(owned.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            AddCookRecipeIndex.RecipeEntry recipe = owned.get(i);
            setButton(DISPLAY_SLOTS[i - from], GuiButton.of(buildIcon(recipe), event -> {
                if (event.getWhoClicked() instanceof Player player) {
                    new RecipeDetailGui(player, recipe, () -> new MyRecipesGui(player, owned, page).open(player)).open(player);
                }
            }));
        }

        if (page > 0) {
            setButton(SLOT_PREV, GuiButton.of(navItem("이전 페이지"), event ->
                    new MyRecipesGui(viewer, owned, page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(SLOT_CLOSE, GuiButton.of(navItem("닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < owned.size()) {
            setButton(SLOT_NEXT, GuiButton.of(navItem("다음 페이지"), event ->
                    new MyRecipesGui(viewer, owned, page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack buildIcon(AddCookRecipeIndex.RecipeEntry recipe) {
        ItemStack stack = AddCookRecipeIndex.resolveIcon(recipe.iconId()).clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(recipe.displayName()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("[" + recipe.furnitureLabel() + "]", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 재료·완성품 보기", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
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
