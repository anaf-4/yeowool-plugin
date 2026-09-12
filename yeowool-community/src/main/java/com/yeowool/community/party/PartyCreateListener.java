package com.yeowool.community.party;

import com.yeowool.core.api.service.MessageService;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /파티 생성} — types the party name in a virtual anvil (same
 * no-real-anvil-block trick as {@code AttendanceRewardAmountListener}/
 * {@code PlaytimeRewardAmountListener}), then {@link PartySizeGui} picks the
 * max size (2-{@link PartyManager#HUD_SLOT_LIMIT}) and actually creates the
 * party. Only the name shape ({@link PartyManager#isValidName}) is checked
 * here (sync, local) — "already in a party"/"name taken" only surface once
 * {@link PartyManager#create} actually runs, same as the plain {@code /파티
 * <이름> <최대인원>} command path.
 */
public final class PartyCreateListener implements Listener {

    private final JavaPlugin plugin;
    private final PartyManager partyManager;
    private final MessageService messages;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public PartyCreateListener(JavaPlugin plugin, PartyManager partyManager, MessageService messages) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.messages = messages;
    }

    public void beginCreate(Player player) {
        pending.add(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            var view = player.openAnvil(null, true);
            if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
                player.sendMessage(Component.text("지금은 모루를 열 수 없습니다.", NamedTextColor.RED));
                pending.remove(player.getUniqueId());
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
        ItemStack preview = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = preview.getItemMeta();
        boolean valid = PartyManager.isValidName(text);
        meta.displayName((valid
                ? Component.text(text, NamedTextColor.GREEN)
                : Component.text(text, NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((valid
                ? Component.text("클릭하여 이 이름으로 다음 단계(최대 인원)로", NamedTextColor.GREEN)
                : Component.text("한글/영문/숫자 2~16자로 입력하세요", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false)));
        preview.setItemMeta(meta);
        return preview;
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
        if (text == null || !PartyManager.isValidName(text)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!pending.remove(player.getUniqueId())) {
            return;
        }
        String partyName = text;
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            new PartySizeGui(partyManager, messages, partyName).open(player);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Only react to OUR anvil closing — opening PartySizeGui right after also closes an inventory
        // (the anvil itself), which fires this same event; clearing `pending` there would be a no-op
        // anyway since it's already removed by the click handler before the close happens.
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
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("여기에 파티 이름을 입력하세요", NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(markerKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(plugin, "party_create_name_anvil");
    }
}
