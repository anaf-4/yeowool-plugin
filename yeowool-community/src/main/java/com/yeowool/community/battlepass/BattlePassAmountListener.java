package com.yeowool.community.battlepass;

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
 * The "금액 설정"/"필요 포인트 설정" buttons in {@link BattlePassRewardEditorGui} open a virtual
 * anvil to type a new number — same two-step preview/confirm trick as {@code
 * AttendanceRewardAmountListener}.
 */
public final class BattlePassAmountListener implements Listener {

    public enum Field { AMOUNT, REQUIRED_POINTS }

    private record PendingEdit(BattlePassTrack track, int tier, Field field) {
    }

    private final JavaPlugin plugin;
    private final BattlePassRewardStore rewardStore;
    private final Map<UUID, PendingEdit> pending = new ConcurrentHashMap<>();

    public BattlePassAmountListener(JavaPlugin plugin, BattlePassRewardStore rewardStore) {
        this.plugin = plugin;
        this.rewardStore = rewardStore;
    }

    public void beginEdit(Player admin, BattlePassTrack track, int tier, Field field) {
        pending.put(admin.getUniqueId(), new PendingEdit(track, tier, field));
        Bukkit.getScheduler().runTask(plugin, () -> {
            var view = admin.openAnvil(null, true);
            if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
                admin.sendMessage(Component.text("지금은 모루를 열 수 없습니다.", NamedTextColor.RED));
                pending.remove(admin.getUniqueId());
                return;
            }
            anvil.setFirstItem(placeholder());
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

    private ItemStack previewItem(String text) {
        ItemStack preview = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = preview.getItemMeta();
        Long amount = parseAmount(text);
        meta.displayName((amount != null
                ? Component.text(String.format("%,d", amount), NamedTextColor.GOLD)
                : Component.text(text, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((amount != null
                ? Component.text("클릭하여 이 값으로 설정", NamedTextColor.GREEN)
                : Component.text("0 이상의 정수를 입력하세요", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false)));
        preview.setItemMeta(meta);
        return preview;
    }

    private Long parseAmount(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
        Long value = parseAmount(text);
        if (value == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        PendingEdit edit = pending.remove(admin.getUniqueId());
        if (edit == null) {
            return;
        }

        var current = rewardStore.get(edit.track(), edit.tier());
        long requiredPoints = edit.field() == Field.REQUIRED_POINTS ? value : current.requiredPoints();
        long amount = edit.field() == Field.AMOUNT ? value : current.amount();
        rewardStore.saveConfig(edit.track(), edit.tier(), requiredPoints, amount, current.currency());
        admin.sendMessage(Component.text((edit.field() == Field.AMOUNT ? "보상 금액을 " : "필요 포인트를 ")
                + String.format("%,d", value) + "(으)로 설정했습니다.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTask(plugin, () -> admin.closeInventory());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Only react to OUR anvil closing - opening the anvil itself closes whatever GUI was
        // open before it (e.g. BattlePassRewardEditorGui), which fires this same event; clearing
        // `pending` on that unrelated close would wipe the edit before the admin can type anything.
        if (event.getInventory() instanceof AnvilInventory anvil && isOurAnvil(anvil)) {
            anvil.setItem(0, null);
            pending.remove(event.getPlayer().getUniqueId());
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

    private ItemStack placeholder() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("여기에 값을 입력하세요", NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(markerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(plugin, "battlepass_reward_anvil");
    }
}
