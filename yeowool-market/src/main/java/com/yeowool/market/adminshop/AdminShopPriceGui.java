package com.yeowool.market.adminshop;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.market.npcshop.ShopBackgroundImages;
import com.yeowool.market.npcshop.ShopLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /상점아이템가격 <상점ID>} — read-only view of {@link AdminShopEditorGui}'s
 * layout (same background, same {@link ShopLayout#USABLE_SLOTS}), except
 * clicking never moves an item — only a right-click on an already-placed
 * item starts {@link ShopPriceAnvilListener}'s 구매가→판매가 anvil wizard.
 * Place items first via {@code /상점아이템설정}/{@code /상점수정}; this screen
 * only prices what's already there.
 */
public final class AdminShopPriceGui extends YeowoolGui {

    private static final int SLOT_PREVIOUS_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 53;

    public AdminShopPriceGui(JavaPlugin plugin, AdminShopStore store, ShopPriceAnvilListener priceListener, String shopId, int pageIndex) {
        super(54, title(plugin, shopId, pageIndex, store.pageCount(shopId)));

        store.rawItemsOf(shopId, pageIndex).forEach((slot, item) -> {
            if (!ShopLayout.USABLE_SLOTS.contains(slot)) {
                return;
            }
            setButton(slot, GuiButton.of(item, event -> {
                if (event.getClick() != ClickType.RIGHT) {
                    return;
                }
                if (event.getWhoClicked() instanceof Player admin) {
                    priceListener.beginEdit(admin, shopId, pageIndex, slot);
                }
            }));
        });

        int pageCount = store.pageCount(shopId);
        if (pageIndex > 0) {
            setButton(SLOT_PREVIOUS_PAGE, GuiButton.of(navArrow("이전 페이지"), event ->
                    new AdminShopPriceGui(plugin, store, priceListener, shopId, pageIndex - 1).open((Player) event.getWhoClicked())));
        }
        if (pageIndex < pageCount - 1) {
            setButton(SLOT_NEXT_PAGE, GuiButton.of(navArrow("다음 페이지"), event ->
                    new AdminShopPriceGui(plugin, store, priceListener, shopId, pageIndex + 1).open((Player) event.getWhoClicked())));
        }
    }

    private static Component title(JavaPlugin plugin, String shopId, int pageIndex, int pageCount) {
        Component fallback = Component.text("[상점아이템가격] " + shopId + " (" + (pageIndex + 1) + "/" + pageCount + ", 우클릭해서 가격 설정)", NamedTextColor.DARK_GREEN);
        int offset = plugin.getConfig().getInt("npc-shop.gui-background-offset", -46);
        return ShopBackgroundImages.title(offset, "shop_item_display", fallback);
    }

    private ItemStack navArrow(String name) {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
