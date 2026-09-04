package com.yeowool.market.npcshop;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * {@code /상점}'s category picker — the "Spectra ShopGUI+" pack's main menu
 * background ({@code shop_gui_menu}) with one button per registered shop
 * (both config.yml ones and in-game-created ones via {@code /상점생성}, since
 * both live in the same shared {@code allShops} map). Category buttons use
 * the pack's blank {@code shop_empty_slot} item — the theme is meant to be
 * read against the background image, not a distinct icon per category.
 */
public final class ShopMainMenuGui extends YeowoolGui {

    public ShopMainMenuGui(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                            Map<String, ShopDefinition> allShops, ShopRotationManager rotationManager) {
        super(54, ShopBackgroundImages.title(plugin.getConfig().getInt("npc-shop.gui-background-offset", -46),
                "shop_gui_menu", Component.text("상점", NamedTextColor.DARK_GREEN)));

        int index = 0;
        for (ShopDefinition shop : allShops.values()) {
            if (index >= ShopLayout.MAIN_MENU_SLOTS.size()) {
                break;
            }
            int slot = ShopLayout.MAIN_MENU_SLOTS.get(index++);
            setButton(slot, GuiButton.of(categoryIcon(shop.title()), event ->
                    new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, 0).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack categoryIcon(String title) {
        ItemStack stack = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance("spectra_shopgui_plus:shop_empty_slot");
            if (custom != null) {
                stack = custom.getItemStack();
            }
        }
        if (stack == null) {
            stack = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(Component.text("클릭하여 둘러보기", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
