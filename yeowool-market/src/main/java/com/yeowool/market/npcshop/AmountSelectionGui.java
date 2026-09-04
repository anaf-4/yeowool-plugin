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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The quantity picker opened by a LEFT (buy) or RIGHT (sell) click on an
 * item in {@link NPCShopGui} — one continuous screen (re-rendered in place,
 * no reopening) instead of ShopGUI+'s separate bulk-buy/bulk-sell GUIs. Uses
 * the {@code shopgui_confirmation} background. Each control uses its own
 * small custom icon ({@code minus_button}/{@code plus_button}/{@code
 * no_button}/{@code yes_button}/{@code pcs_button}, registered by hand
 * alongside the pack's own items) so the button is exactly where it
 * visually appears, instead of trying to composite several font-image
 * glyphs into the title with hand-tuned pixel offsets.
 */
public final class AmountSelectionGui extends YeowoolGui {

    public enum Mode { BUY, SELL }

    private static final int SLOT_REMOVE_1 = 18;
    private static final int SLOT_REMOVE_16 = 19;
    private static final int SLOT_REMOVE_32 = 20;
    private static final int SLOT_ITEM = 22;
    private static final int SLOT_ADD_1 = 24;
    private static final int SLOT_ADD_16 = 25;
    private static final int SLOT_ADD_32 = 26;
    private static final int SLOT_SET_MAX = 49;
    private static final int[] SLOTS_CANCEL = {38, 39};
    private static final int[] SLOTS_CONFIRM = {41, 42};

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Map<String, ShopDefinition> allShops;
    private final ShopDefinition shop;
    private final ShopRotationManager rotationManager;
    private final int viewPage;
    private final ShopItem item;
    private final Mode mode;
    private final int maxAmount;
    /** Resolved once (custom-item lookups aren't free) and cloned per render instead of re-resolving on every ± click. */
    private final ItemStack prototype;
    private int amount;

    public AmountSelectionGui(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, Map<String, ShopDefinition> allShops,
                               ShopDefinition shop, ShopRotationManager rotationManager, int viewPage, ShopItem item, Mode mode, int initialAmount) {
        super(54, ShopBackgroundImages.title(plugin.getConfig().getInt("npc-shop.gui-background-offset", -46),
                "shopgui_confirmation",
                Component.text(mode == Mode.BUY ? "구매 수량 선택" : "판매 수량 선택", NamedTextColor.DARK_GREEN)));
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.allShops = allShops;
        this.shop = shop;
        this.rotationManager = rotationManager;
        this.viewPage = viewPage;
        this.item = item;
        this.mode = mode;
        this.prototype = ItemResolver.build(item, 1, plugin.getLogger());
        this.maxAmount = Math.max(1, prototype.getMaxStackSize());
        this.amount = clamp(initialAmount);

        setButton(SLOT_REMOVE_1, GuiButton.of(customIcon("minus_button", Material.RED_STAINED_GLASS_PANE, "1개 감소"), e -> setAmount(amount - 1)));
        setButton(SLOT_REMOVE_16, GuiButton.of(customIcon("minus_button", Material.RED_STAINED_GLASS_PANE, "16개 감소"), e -> setAmount(amount - 16)));
        setButton(SLOT_REMOVE_32, GuiButton.of(customIcon("minus_button", Material.RED_STAINED_GLASS_PANE, "32개 감소"), e -> setAmount(amount - 32)));
        setButton(SLOT_ADD_1, GuiButton.of(customIcon("plus_button", Material.LIME_STAINED_GLASS_PANE, "1개 증가"), e -> setAmount(amount + 1)));
        setButton(SLOT_ADD_16, GuiButton.of(customIcon("plus_button", Material.LIME_STAINED_GLASS_PANE, "16개 증가"), e -> setAmount(amount + 16)));
        setButton(SLOT_ADD_32, GuiButton.of(customIcon("plus_button", Material.LIME_STAINED_GLASS_PANE, "32개 증가"), e -> setAmount(amount + 32)));
        // NO/YES/64 already have their own art baked into the shop_gui_confirmation.png
        // background — putting the textured no_button/yes_button/pcs_button items on top
        // just duplicated that art. Use shop_empty_slot (a real item, so it still carries
        // a display name for the hover tooltip) instead, which renders blank so only the
        // background's own graphic shows, with the name as the text.
        setButton(SLOT_SET_MAX, GuiButton.of(customIcon("shop_empty_slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "최대(" + maxAmount + "개)로 설정"), e -> setAmount(maxAmount)));
        // shop_empty_slot renders blank either way, so putting it in BOTH
        // slots (unlike the textured no_button/yes_button before) doesn't
        // duplicate any visible art — it just makes the whole 2-slot area
        // hoverable/clickable, matching how wide the NO/YES box actually
        // looks in shop_gui_confirmation.png.
        for (int slot : SLOTS_CANCEL) {
            setButton(slot, GuiButton.of(customIcon("shop_empty_slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "취소"), e -> reopenShop((Player) e.getWhoClicked())));
        }
        for (int slot : SLOTS_CONFIRM) {
            setButton(slot, GuiButton.of(customIcon("shop_empty_slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE, mode == Mode.BUY ? "구매 확정" : "판매 확정"),
                    e -> confirm((Player) e.getWhoClicked())));
        }

        renderItemPreview();
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(maxAmount, value));
    }

    private void setAmount(int newAmount) {
        amount = clamp(newAmount);
        renderItemPreview();
    }

    private void renderItemPreview() {
        ItemStack preview = prototype.clone();
        preview.setAmount(amount);
        ItemMeta meta = preview.getItemMeta();
        long unitPrice = mode == Mode.BUY ? item.buyPrice() : item.sellPrice();
        long total = unitPrice * amount;

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text("수량: " + amount, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text((mode == Mode.BUY ? "구매 총액: " : "판매 총액: ") + String.format("%,d", total) + item.currency().displayName(),
                mode == Mode.BUY ? NamedTextColor.GREEN : NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        preview.setItemMeta(meta);
        getInventory().setItem(SLOT_ITEM, preview);
    }

    private void confirm(Player player) {
        if (mode == Mode.BUY) {
            ShopTransactions.buy(plugin, core, messages, player, item, amount);
        } else {
            ShopTransactions.sellAmount(core, messages, player, item, amount);
        }
        reopenShop(player);
    }

    private void reopenShop(Player player) {
        new NPCShopGui(plugin, core, messages, allShops, shop, rotationManager, viewPage).open(player);
    }

    private ItemStack customIcon(String customItemId, Material fallbackMaterial, String name) {
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
