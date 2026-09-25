package com.yeowool.federation.shop;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** /연합 상점 — lists federation shops; unlocked ones open through the normal /상점 path (so the gate and payment behave as usual). */
public final class FederationShopGui extends YeowoolGui {

    private static final int MAX_SLOTS = 54;

    public FederationShopGui(List<FederationShop> shops, int federationLevel) {
        super(sizeFor(shops.size()), Component.text("연합 상점", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < shops.size() && i < MAX_SLOTS; i++) {
            FederationShop shop = shops.get(i);
            boolean unlocked = federationLevel >= shop.minLevel();
            setButton(i, GuiButton.of(icon(shop, unlocked), event -> {
                if (!unlocked) {
                    return;
                }
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                player.performCommand("상점 " + shop.shopId());
            }));
        }
    }

    private static int sizeFor(int shopCount) {
        int rows = Math.max(1, (Math.min(shopCount, MAX_SLOTS) + 8) / 9);
        return rows * 9;
    }

    private static ItemStack icon(FederationShop shop, boolean unlocked) {
        ItemStack stack = new ItemStack(unlocked ? Material.CHEST : Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(shop.name(), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(unlocked ? "클릭해서 열기" : "연합 Lv." + shop.minLevel() + " 필요", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
