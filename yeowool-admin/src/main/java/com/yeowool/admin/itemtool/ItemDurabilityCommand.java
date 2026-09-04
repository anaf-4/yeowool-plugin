package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /아이템내구도 무한} — toggles unbreakable on the item in hand
 * (Bukkit's {@code ItemMeta#setUnbreakable}), so it never loses durability
 * regardless of use.
 */
public final class ItemDurabilityCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;

    public ItemDurabilityCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1 || !args[0].equals("무한")) {
            messages.send(player, "itemtool.durability-usage");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            messages.send(player, "itemtool.need-item-in-hand");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        boolean newState = !meta.isUnbreakable();
        meta.setUnbreakable(newState);
        item.setItemMeta(meta);

        messages.send(player, newState ? "itemtool.durability-enabled" : "itemtool.durability-disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("무한"), args[0]);
        }
        return List.of();
    }
}
