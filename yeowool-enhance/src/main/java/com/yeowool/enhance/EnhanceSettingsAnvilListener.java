package com.yeowool.enhance;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link EnhanceSettingsDetailGui}'s "type a number in the rename field,
 * click to confirm" inputs for 온/재료 개수 — same anvil trick as
 * {@code ShopPriceAnvilListener} in yeowool-market. On confirm, saves via
 * {@link EnhanceCostManager} and reopens the detail GUI for that level so an
 * admin can keep tuning +N강 after +N강 without leaving the flow.
 */
public final class EnhanceSettingsAnvilListener implements Listener {

    private enum Kind { CURRENCY, MATERIAL_AMOUNT }

    private record Pending(int level, Kind kind, String materialId) {
    }

    private final JavaPlugin plugin;
    private final EnhanceService service;
    private final EnhanceCostManager costs;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public EnhanceSettingsAnvilListener(JavaPlugin plugin, EnhanceService service, EnhanceCostManager costs) {
        this.plugin = plugin;
        this.service = service;
        this.costs = costs;
    }

    public void beginCurrencyEdit(Player admin, int level) {
        pending.put(admin.getUniqueId(), new Pending(level, Kind.CURRENCY, null));
        openAnvil(admin, "필요 온 입력 (현재 " + String.format("%,d", costs.costFor(level).currency()) + ")");
    }

    public void beginMaterialAmountEdit(Player admin, int level, String materialId) {
        pending.put(admin.getUniqueId(), new Pending(level, Kind.MATERIAL_AMOUNT, materialId));
        openAnvil(admin, "필요 개수 입력 (" + EnhanceMaterialResolver.displayName(materialId) + ")");
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
        Long value = parseValue(text);
        if (value == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        Pending edit = pending.remove(admin.getUniqueId());
        if (edit == null) {
            return;
        }

        if (edit.kind() == Kind.CURRENCY) {
            costs.setCurrency(edit.level(), value);
            admin.sendMessage(Component.text("+" + edit.level() + "강 필요 온을 " + String.format("%,d", value) + "으로 설정했습니다.", NamedTextColor.GREEN));
        } else {
            costs.setMaterial(edit.level(), edit.materialId(), (int) Math.max(1, value));
            admin.sendMessage(Component.text("+" + edit.level() + "강 재료를 " + EnhanceMaterialResolver.displayName(edit.materialId())
                    + " x" + value + "개로 설정했습니다.", NamedTextColor.GREEN));
        }
        int level = edit.level();
        Bukkit.getScheduler().runTask(plugin, () -> {
            admin.closeInventory();
            new EnhanceSettingsDetailGui(service, costs, this, level).open(admin);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil) || !isOurAnvil(anvil)) {
            return;
        }
        anvil.setItem(0, null);
        pending.remove(event.getPlayer().getUniqueId());
    }

    private Long parseValue(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ItemStack previewItem(String text) {
        ItemStack preview = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = preview.getItemMeta();
        Long value = parseValue(text);
        meta.displayName((value != null
                ? Component.text(String.format("%,d", value), NamedTextColor.GOLD)
                : Component.text(text, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((value != null
                ? Component.text("클릭하여 이 값으로 설정", NamedTextColor.GREEN)
                : Component.text("0 이상의 정수를 입력하세요", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false)));
        preview.setItemMeta(meta);
        return preview;
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
        return new NamespacedKey(plugin, "enhance_settings_anvil");
    }
}
