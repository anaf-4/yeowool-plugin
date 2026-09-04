package com.yeowool.life.bag;

import com.yeowool.core.api.service.MessageService;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-clicking {@link #TICKET_ID} opens {@link BagExpandSelectGui}. */
public final class TicketChestListener implements Listener {

    static final String TICKET_ID = "moafarm_items:ticket_chest";

    private final BagManager manager;
    private final MessageService messages;

    public TicketChestListener(BagManager manager, MessageService messages) {
        this.manager = manager;
        this.messages = messages;
    }

    static boolean isTicket(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return false;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null && custom.getNamespacedID().equals(TICKET_ID);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isTicket(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (!manager.isLoaded(event.getPlayer().getUniqueId())) {
            messages.send(event.getPlayer(), "bag.not-loaded-yet");
            return;
        }
        new BagExpandSelectGui(manager, messages, event.getPlayer()).open(event.getPlayer());
    }
}
