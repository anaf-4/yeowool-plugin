package com.yeowool.community.vote;

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * 온/별조각 amount input for {@link VoteRewardEditorGui} — the same virtual-anvil, preview-then-click
 * flow as {@code AttendanceRewardAmountListener} (0 is allowed here, e.g. a milestone with only items).
 */
public final class VoteRewardAmountListener implements Listener {

    private record Edit(int threshold, boolean stardust, Runnable onSaved) {
    }

    private final JavaPlugin plugin;
    private final VoteRepository repository;
    private final Executor executor;
    private final Map<UUID, Edit> pending = new ConcurrentHashMap<>();

    VoteRewardAmountListener(JavaPlugin plugin, VoteRepository repository, Executor executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
    }

    void beginEdit(Player admin, int threshold, boolean stardust, Runnable onSaved) {
        pending.put(admin.getUniqueId(), new Edit(threshold, stardust, onSaved));
        Bukkit.getScheduler().runTask(plugin, () -> {
            var view = admin.openAnvil(null, true);
            if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
                admin.sendMessage(Component.text("지금은 모루를 열 수 없습니다.", NamedTextColor.RED));
                pending.remove(admin.getUniqueId());
                return;
            }
            anvil.setFirstItem(placeholder(stardust));
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
        Long amount = parseAmount(text);
        ItemStack preview = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = preview.getItemMeta();
        meta.displayName((amount != null
                ? Component.text(String.format("%,d", amount), NamedTextColor.GOLD)
                : Component.text(text, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((amount != null
                ? Component.text("클릭하여 이 값으로 설정", NamedTextColor.GREEN)
                : Component.text("0 이상의 정수를 입력하세요", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false)));
        preview.setItemMeta(meta);
        event.setResult(preview);
    }

    private Long parseAmount(String text) {
        try {
            long value = Long.parseLong(text.trim().replace(",", ""));
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory anvil) || event.getSlot() != 2 || !isOurAnvil(anvil)
                || !(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        String text = anvilView.getRenameText();
        Long amount = text == null ? null : parseAmount(text);
        if (amount == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        Edit edit = pending.remove(admin.getUniqueId());
        if (edit == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> admin.closeInventory());
        executor.execute(() -> {
            try {
                repository.saveAmount(edit.threshold(), edit.stardust(), amount);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.sendMessage(Component.text((edit.stardust() ? "별조각" : "온") + " 보상을 "
                            + String.format("%,d", amount) + "(으)로 설정했습니다.", NamedTextColor.GREEN));
                    edit.onSaved().run();
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "추천 보상 금액 저장 실패 (" + edit.threshold() + ")", e);
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // only our own anvil — opening it closes the editor GUI, which must not clear the pending edit
        if (event.getInventory() instanceof AnvilInventory anvil && isOurAnvil(anvil)) {
            anvil.setItem(0, null);
            pending.remove(event.getPlayer().getUniqueId());
        }
    }

    private boolean isOurAnvil(Inventory inventory) {
        if (!(inventory instanceof AnvilInventory anvil)) {
            return false;
        }
        ItemStack first = anvil.getItem(0);
        return first != null && !first.getType().isAir() && first.getItemMeta() != null
                && first.getItemMeta().getPersistentDataContainer().has(markerKey(), PersistentDataType.BYTE);
    }

    private ItemStack placeholder(boolean stardust) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(stardust ? "별조각 개수를 입력하세요" : "온 금액을 입력하세요", NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(markerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(plugin, "vote_reward_amount_anvil");
    }
}
