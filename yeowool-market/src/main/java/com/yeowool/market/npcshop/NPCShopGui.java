package com.yeowool.market.npcshop;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.util.ItemResolver;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fixed-price NPC shop, reskinned after the "Spectra ShopGUI+" ItemsAdder
 * asset pack: a {@code shop_item_display} background, LEFT/RIGHT clicks open
 * {@link AmountSelectionGui} to buy/sell a chosen quantity (instead of the
 * old instant-buy-1 / sell-everything-at-once), and SHIFT_RIGHT or MIDDLE
 * still instantly sells every matching item (mirrors the pack's
 * {@code clickActions: SHIFT_RIGHT/MIDDLE: SELL_ALL}). A single
 * {@link ShopDefinition} can span multiple pages via {@link ShopItem#page()};
 * this GUI only ever renders one page at a time, picked by {@code viewPage},
 * with 이전/다음 buttons at slots 45/53 (icons: {@code shop_back_button}/
 * {@code shop_next_button}) and a 뒤로가기 button (slot 4, icon: {@code
 * home_button}) back to {@link ShopMainMenuGui}. Page 1 only shows 다음(53);
 * the last page only shows 이전(45); any page in between shows both.
 */
public final class NPCShopGui extends YeowoolGui {

    private static final int SLOT_BACK = 4;
    private static final int SLOT_PREVIOUS_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 53;

    public NPCShopGui(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, Map<String, ShopDefinition> allShops,
                       ShopDefinition shop, ShopRotationManager rotationManager, int viewPage) {
        super(shop.size(), title(plugin, shop, viewPage));

        if (shop.decoration() != null) {
            ItemStack filler = buildDecoration(shop.decoration());
            for (int slot = 0; slot < shop.size(); slot++) {
                setButton(slot, GuiButton.display(filler));
            }
        }

        List<ShopItem> visible = new ArrayList<>();
        for (ShopItem item : shop.items()) {
            if (item.page() == viewPage) {
                visible.add(item);
            }
        }
        visible.addAll(rotationManager.activeItemsFor(shop));

        for (ShopItem item : visible) {
            if (item.slot() < 0 || item.slot() >= shop.size()) {
                continue;
            }
            setButton(item.slot(), GuiButton.of(buildIcon(item, shop.mode(), plugin), event -> {
                Player player = (Player) event.getWhoClicked();
                ClickType click = event.getClick();
                if (click == ClickType.SHIFT_RIGHT || click == ClickType.MIDDLE) {
                    if (shop.mode().allowsSelling()) {
                        ShopTransactions.sellAll(core, messages, player, item);
                        new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, viewPage).open(player);
                    }
                } else if (click == ClickType.RIGHT) {
                    if (shop.mode().allowsSelling() && item.isSellable()) {
                        new AmountSelectionGui(plugin, core, messages, allShops, shop, rotationManager, viewPage, item,
                                AmountSelectionGui.Mode.SELL, 1).open(player);
                    }
                } else if (shop.mode().allowsBuying() && item.isBuyable()) {
                    new AmountSelectionGui(plugin, core, messages, allShops, shop, rotationManager, viewPage, item,
                            AmountSelectionGui.Mode.BUY, 1).open(player);
                }
            }));
        }

        if (shop.size() > SLOT_BACK) {
            setButton(SLOT_BACK, GuiButton.of(navItem("home_button", Material.BARRIER, "메인 메뉴로"),
                    event -> new ShopMainMenuGui(plugin, core, messages, allShops, rotationManager).open((Player) event.getWhoClicked())));
        }

        // Only a full 54-slot shop has slot 53 at all, so anything smaller
        // simply can't page — fine, since it'd need more items than it has
        // room for on one page to need paging in the first place.
        if (shop.size() > SLOT_NEXT_PAGE) {
            if (viewPage > 0) {
                setButton(SLOT_PREVIOUS_PAGE, GuiButton.of(navItem("shop_back_button", Material.ARROW, "이전 페이지"),
                        event -> new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, viewPage - 1).open((Player) event.getWhoClicked())));
            }
            if (viewPage < shop.pageCount() - 1) {
                setButton(SLOT_NEXT_PAGE, GuiButton.of(navItem("shop_next_button", Material.ARROW, "다음 페이지"),
                        event -> new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, viewPage + 1).open((Player) event.getWhoClicked())));
            }
        }
    }

    private static Component title(JavaPlugin plugin, ShopDefinition shop, int viewPage) {
        Component fallback = Component.text(shop.title()
                + (shop.pageCount() > 1 ? " (" + (viewPage + 1) + "/" + shop.pageCount() + ")" : ""), NamedTextColor.DARK_GREEN);
        int offset = plugin.getConfig().getInt("npc-shop.gui-background-offset", -46);
        return ShopBackgroundImages.title(offset, "shop_item_display", fallback);
    }

    private ItemStack buildDecoration(ShopDefinition.Decoration decoration) {
        ItemStack stack = new ItemStack(decoration.material());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        if (decoration.hasCustomModelData()) {
            meta.setCustomModelData(decoration.customModelData());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(ShopItem item, ShopMode mode, JavaPlugin plugin) {
        ItemStack stack = ItemResolver.build(item, 1, plugin.getLogger());
        ItemMeta meta = stack.getItemMeta();

        boolean showBuy = mode.allowsBuying() && item.isBuyable();
        boolean showSell = mode.allowsSelling() && item.isSellable();
        String currencyName = item.currency().displayName();

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        if (showBuy) {
            lore.add(Component.text("구매가: " + String.format("%,d", item.buyPrice()) + currencyName, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (showSell) {
            lore.add(Component.text("판매가: " + String.format("%,d", item.sellPrice()) + currencyName, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (showBuy || showSell) {
            lore.add(Component.text(" ", NamedTextColor.GRAY));
        }
        if (showBuy) {
            lore.add(legacyLine(ShopBackgroundImages.glyph("icon_left_click", "▸") + " &7좌클릭: &f수량 선택 후 구매"));
        }
        if (showSell) {
            lore.add(legacyLine(ShopBackgroundImages.glyph("icon_right_click", "▸") + " &7우클릭: &f수량 선택 후 판매"));
            lore.add(legacyLine(ShopBackgroundImages.glyph("icon_red_alert", "!") + " &7Shift+우클릭/휠클릭: &f전량 즉시 판매"));
        }
        meta.lore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * These lore lines are built with {@code &}-prefixed codes (see {@link
     * #buildIcon}), so they need {@code legacyAmpersand()} — {@code
     * legacySection()} only recognizes the {@code §} character and was
     * leaving every {@code &7}/{@code &f}/{@code &r} showing up as literal
     * text instead of actually coloring anything.
     */
    private Component legacyLine(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize("&r" + legacy).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack navItem(String customItemId, Material fallbackMaterial, String name) {
        ItemStack stack = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance("spectra_shopgui_plus:" + customItemId);
            if (custom != null) {
                stack = custom.getItemStack();
            }
        }
        if (stack == null) {
            stack = new ItemStack(fallbackMaterial);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
