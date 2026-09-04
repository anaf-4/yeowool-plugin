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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code /상점수정 <상점ID>} — only {@link ShopLayout#USABLE_SLOTS} are
 * free-edit slots (place items to add them to the shop, remove to delete,
 * close to save), mirroring {@code StarterKitEditorGui}'s pattern. Every
 * other slot — the outer border row/column plus 뒤로가기/이전/다음 at 4/45/53 —
 * is left completely empty rather than filled with a placeholder item. Also
 * renders the same {@code shop_item_display} background {@link
 * com.yeowool.market.npcshop.NPCShopGui} uses so the admin can see exactly
 * where each slot lands relative to the artwork while placing items.
 */
public final class AdminShopEditorGui extends YeowoolGui {

    private static final int SLOT_PREVIOUS_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final Set<Integer> RESERVED_SLOTS = Set.of(4, SLOT_PREVIOUS_PAGE, SLOT_NEXT_PAGE);

    private final AdminShopStore store;
    private final String shopId;
    private final int pageIndex;

    public AdminShopEditorGui(JavaPlugin plugin, AdminShopStore store, String shopId, int pageIndex) {
        super(54, title(plugin, shopId, pageIndex, store.pageCount(shopId)));
        this.store = store;
        this.shopId = shopId;
        this.pageIndex = pageIndex;

        for (int slot : ShopLayout.USABLE_SLOTS) {
            setEditableSlot(slot);
        }
        store.rawItemsOf(shopId, pageIndex).forEach((slot, item) -> {
            if (ShopLayout.USABLE_SLOTS.contains(slot)) {
                getInventory().setItem(slot, item);
            }
        });

        int pageCount = store.pageCount(shopId);
        if (pageIndex > 0) {
            setButton(SLOT_PREVIOUS_PAGE, GuiButton.of(navArrow("이전 페이지"), event ->
                    new AdminShopEditorGui(plugin, store, shopId, pageIndex - 1).open((Player) event.getWhoClicked())));
        }
        if (pageIndex < pageCount - 1) {
            setButton(SLOT_NEXT_PAGE, GuiButton.of(navArrow("다음 페이지"), event ->
                    new AdminShopEditorGui(plugin, store, shopId, pageIndex + 1).open((Player) event.getWhoClicked())));
        }
        // Slot 4 (뒤로가기 자리) stays empty here too — nothing to click while editing, just reserved so
        // it lines up with where the live view's 메인 메뉴로 button will sit.
    }

    @Override
    public void onClose(Player player) {
        Map<Integer, ItemStack> snapshot = new LinkedHashMap<>();
        for (int slot : ShopLayout.USABLE_SLOTS) {
            ItemStack stack = getInventory().getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                snapshot.put(slot, stack);
            }
        }
        store.savePage(shopId, pageIndex, snapshot);
        player.sendMessage(Component.text("상점 [" + shopId + "] " + (pageIndex + 1) + "페이지를 저장했습니다. (" + snapshot.size() + "개 아이템)", NamedTextColor.GREEN));
    }

    private static Component title(JavaPlugin plugin, String shopId, int pageIndex, int pageCount) {
        Component fallback = Component.text("[상점수정] " + shopId + " (" + (pageIndex + 1) + "/" + pageCount + ", 닫으면 저장됨)", NamedTextColor.DARK_GREEN);
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
