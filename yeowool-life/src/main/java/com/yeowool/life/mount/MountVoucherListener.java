package com.yeowool.life.mount;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-clicking a voucher grants the mount's MCPets permission permanently through LuckPerms
 * and consumes one voucher. LuckPerms applies the change a moment later, so a short in-memory
 * guard stops a fast double-click from spending a second voucher.
 */
public final class MountVoucherListener implements Listener {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MountVoucherItem voucherItem;
    private final Map<String, MountDefinition> mounts;
    private final Set<String> granting = ConcurrentHashMap.newKeySet();

    public MountVoucherListener(JavaPlugin plugin, MessageService messages, MountVoucherItem voucherItem, Map<String, MountDefinition> mounts) {
        this.plugin = plugin;
        this.messages = messages;
        this.voucherItem = voucherItem;
        this.mounts = mounts;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        var mountId = voucherItem.read(event.getItem());
        if (mountId.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        MountDefinition mount = mounts.get(mountId.get());
        if (mount == null) {
            messages.send(player, "mount.unknown", Placeholder.unparsed("id", mountId.get()));
            return;
        }
        String guardKey = player.getUniqueId() + ":" + mount.id();
        if (player.hasPermission(mount.permission()) || granting.contains(guardKey)) {
            messages.send(player, "mount.already-owned", Placeholder.unparsed("mount", mount.displayName()));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(hand.getAmount() > 1 ? hand.asQuantity(hand.getAmount() - 1) : null);
        granting.add(guardKey);
        Bukkit.getScheduler().runTaskLater(plugin, () -> granting.remove(guardKey), 20L * 10);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + player.getName() + " permission set " + mount.permission() + " true");
        plugin.getLogger().info("탈것 이용권 사용: " + player.getName() + " → " + mount.id() + " (" + mount.permission() + ")");
        messages.send(player, "mount.unlocked", Placeholder.unparsed("mount", mount.displayName()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }
}
