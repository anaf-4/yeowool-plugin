package com.yeowool.admin.check;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Right-clicking a {@link CheckItem} credits the holder's matching balance and consumes one from the stack. */
public final class CheckRedeemListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public CheckRedeemListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!CheckItem.isCheck(plugin, item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        long amount = CheckItem.amount(plugin, item);
        CurrencyType currency = CheckItem.currency(plugin, item);
        if (amount <= 0) {
            return;
        }

        item.setAmount(item.getAmount() - 1);

        boolean cash = currency == CurrencyType.CASH;
        if (cash) {
            core.economyData().modifyCashBalance(player.getUniqueId(), amount, "YeowoolAdmin", "수표 사용");
        } else {
            core.economyData().modifyBalance(player.getUniqueId(), amount, "YeowoolAdmin", "수표 사용");
        }
        messages.send(player, "check.redeem-success",
                Placeholder.unparsed("amount", String.format("%,d", amount)),
                Placeholder.unparsed("currency", currency.displayName()));
    }
}
