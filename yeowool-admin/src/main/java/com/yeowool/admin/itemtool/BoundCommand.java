package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /귀속} — toggles bound (soulbound) on the item in the OP's main
 * hand. A bound item can't be dropped by a non-OP holder (see
 * {@link BoundItemProtectionListener}), but an OP can always drop it, per
 * the explicit requirement that binding never traps an operator's own item.
 */
public final class BoundCommand implements CommandExecutor {

    private final MessageService messages;
    private final ItemFlags itemFlags;

    public BoundCommand(MessageService messages, ItemFlags itemFlags) {
        this.messages = messages;
        this.itemFlags = itemFlags;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        var item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            messages.send(player, "itemtool.need-item-in-hand");
            return true;
        }

        boolean newState = !itemFlags.isBound(item);
        itemFlags.setBound(item, newState);
        messages.send(player, newState ? "itemtool.bound-set" : "itemtool.bound-unset");
        return true;
    }
}
