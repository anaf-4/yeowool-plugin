package com.yeowool.market.adminshop;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.market.npcshop.ShopPricedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AdminShopPriceGui}'s right-click-to-price flow: a virtual anvil
 * asks for the 구매가, then (after that's confirmed) a second anvil asks for
 * the 판매가, then both are stamped onto the item at that exact shop/page/slot
 * and saved. Same "type a number in the rename field, click to confirm"
 * trick as {@code BattlePassAmountListener}. {@code -1} means "off" for that
 * direction (구매불가/판매불가), matching the old {@code /상점아이템설정 <상점ID>
 * <구매가> <판매가> ...} command's convention. Currency/엄격매칭 are left as
 * whatever the item already had — this screen only ever changes the two
 * price numbers.
 */
public final class ShopPriceAnvilListener implements Listener {

    private enum Step { BUY, SELL }

    private record PendingPrice(String shopId, int pageIndex, int slot, long buyPrice, Step step) {
    }

    private final JavaPlugin plugin;
    private final AdminShopStore store;
    private final Map<UUID, PendingPrice> pending = new ConcurrentHashMap<>();
    // Set right before we close our own anvil to immediately reopen one for the next step, so
    // onClose (which fires from that self-triggered close) knows not to wipe the edit we just
    // advanced to Step.SELL.
    private final Set<UUID> transitioning = ConcurrentHashMap.newKeySet();

    public ShopPriceAnvilListener(JavaPlugin plugin, AdminShopStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void beginEdit(Player admin, String shopId, int pageIndex, int slot) {
        pending.put(admin.getUniqueId(), new PendingPrice(shopId, pageIndex, slot, -1, Step.BUY));
        openAnvil(admin, "구매가 입력 (구매불가: -1)");
    }

    private void openAnvil(Player admin, String hint) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var view = admin.openAnvil(null, true);
            if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
                admin.sendMessage(Component.text("지금은 모루를 열 수 없습니다.", NamedTextColor.RED));
                pending.remove(admin.getUniqueId());
                return;
            }
            anvil.setFirstItem(placeholder(hint));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isOurAnvil(event.getInventory())) {
            return;
        }
        String text = event.getView().getRenameText();
        if (text == null || text.isBlank()) {
            return;
        }
        event.setResult(previewItem(text));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory anvil) || event.getSlot() != 2 || !isOurAnvil(anvil)) {
            return;
        }
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        String text = anvilView.getRenameText();
        if (text == null || text.isBlank()) {
            return;
        }
        Long value = parsePrice(text);
        if (value == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        PendingPrice edit = pending.remove(admin.getUniqueId());
        if (edit == null) {
            return;
        }

        if (edit.step() == Step.BUY) {
            pending.put(admin.getUniqueId(), new PendingPrice(edit.shopId(), edit.pageIndex(), edit.slot(), value, Step.SELL));
            transitioning.add(admin.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.closeInventory();
                openAnvil(admin, "판매가 입력 (판매불가: -1)");
            });
            return;
        }

        applyPrice(admin, edit.shopId(), edit.pageIndex(), edit.slot(), edit.buyPrice(), value);
        Bukkit.getScheduler().runTask(plugin, () -> admin.closeInventory());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil) || !isOurAnvil(anvil)) {
            return;
        }
        anvil.setItem(0, null);
        UUID uuid = event.getPlayer().getUniqueId();
        if (transitioning.remove(uuid)) {
            return;
        }
        pending.remove(uuid);
    }

    private void applyPrice(Player admin, String shopId, int pageIndex, int slot, long buyPrice, long sellPrice) {
        Map<Integer, ItemStack> page = new LinkedHashMap<>(store.rawItemsOf(shopId, pageIndex));
        ItemStack item = page.get(slot);
        if (item == null) {
            admin.sendMessage(Component.text("그 사이 아이템이 없어졌습니다.", NamedTextColor.RED));
            return;
        }
        CurrencyType currency = ShopPricedItem.currency(plugin, item);
        boolean strictMatch = ShopPricedItem.strictMatch(plugin, item);
        ShopPricedItem.stamp(plugin, item, buyPrice, sellPrice, currency, strictMatch);
        page.put(slot, item);
        store.savePage(shopId, pageIndex, page);
        admin.sendMessage(Component.text("가격을 설정했습니다. (구매가: " + priceLabel(buyPrice, currency)
                + ", 판매가: " + priceLabel(sellPrice, currency) + ")", NamedTextColor.GREEN));
    }

    private String priceLabel(long price, CurrencyType currency) {
        return price > 0 ? String.format("%,d", price) + currency.displayName() : "불가";
    }

    private ItemStack previewItem(String text) {
        ItemStack preview = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = preview.getItemMeta();
        Long price = parsePrice(text);
        meta.displayName((price != null
                ? Component.text(price == -1 ? "불가" : String.format("%,d", price), NamedTextColor.GOLD)
                : Component.text(text, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((price != null
                ? Component.text("클릭하여 이 값으로 설정", NamedTextColor.GREEN)
                : Component.text("0 이상의 정수 또는 -1(불가)을 입력하세요", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false)));
        preview.setItemMeta(meta);
        return preview;
    }

    private Long parsePrice(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value >= -1 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isOurAnvil(org.bukkit.inventory.Inventory inventory) {
        if (!(inventory instanceof AnvilInventory anvil)) {
            return false;
        }
        ItemStack first = anvil.getItem(0);
        if (first == null || first.getType().isAir() || first.getItemMeta() == null) {
            return false;
        }
        return first.getItemMeta().getPersistentDataContainer().has(markerKey(), PersistentDataType.BYTE);
    }

    private ItemStack placeholder(String hint) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(hint, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(markerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(plugin, "shop_price_anvil");
    }
}
