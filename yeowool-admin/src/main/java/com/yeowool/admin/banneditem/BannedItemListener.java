package com.yeowool.admin.banneditem;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * {@link BanType#CRAFT} only blocks {@link CraftItemEvent}. {@link BanType#POSSESSION}
 * blocks that too, plus pickup, plus actively confiscates the item wherever it turns
 * up in an inventory (same click/join-scan pattern as {@code DuplicationScanTask}) —
 * so a possession-banned item genuinely can never sit in a player's inventory.
 * {@code yeowool.admin.super} (총관리진) bypasses everything, for staff who need to
 * hold a banned item to investigate/despawn it.
 */
public final class BannedItemListener implements Listener {

    private static final String BYPASS_PERMISSION = "yeowool.admin.super";

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final BannedItemManager manager;

    public BannedItemListener(YeowoolCoreAPI core, MessageService messages, BannedItemManager manager) {
        this.core = core;
        this.messages = messages;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        Material material = result.getType();
        if (!manager.isBanned(material, BanType.POSSESSION) && !manager.isBanned(material, BanType.CRAFT)) {
            return;
        }
        if (event.getWhoClicked().hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            messages.send(player, "banneditem.craft-blocked", Placeholder.unparsed("item", material.name()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Material material = event.getItem().getItemStack().getType();
        if (!manager.isBanned(material, BanType.POSSESSION) || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        confiscateIfBanned(player, event.getCurrentItem());
        confiscateIfBanned(player, event.getCursor());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        sweep(player, player.getInventory());
        sweep(player, player.getEnderChest());
    }

    /** Called right after {@code /아이템밴 추가} so an already-held item is confiscated immediately, not on the next click. */
    public void confiscateFromAllOnline(Material material) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(BYPASS_PERMISSION)) {
                continue;
            }
            sweepMaterial(player, player.getInventory(), material);
            sweepMaterial(player, player.getEnderChest(), material);
        }
    }

    private void sweep(Player player, Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            confiscateIfBanned(player, inventory.getItem(i));
        }
    }

    private void sweepMaterial(Player player, Inventory inventory, Material material) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == material) {
                confiscateIfBanned(player, stack);
            }
        }
    }

    private void confiscateIfBanned(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !manager.isBanned(stack.getType(), BanType.POSSESSION)) {
            return;
        }
        int amount = stack.getAmount();
        Material material = stack.getType();
        stack.setAmount(0);

        messages.send(player, "banneditem.confiscated",
                Placeholder.unparsed("item", material.name()), Placeholder.unparsed("amount", String.valueOf(amount)));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yeowool.admin.alerts")) {
                messages.send(staff, "banneditem.confiscate-alert",
                        Placeholder.unparsed("target", player.getName()),
                        Placeholder.unparsed("item", material.name()),
                        Placeholder.unparsed("amount", String.valueOf(amount)));
            }
        }
        core.logs().log("YeowoolAdmin", "banneditem.confiscated", player.getUniqueId(),
                "아이템밴된 아이템 자동 회수", Map.of(
                        "item", material.name(),
                        "amount", String.valueOf(amount)
                ));
    }
}
