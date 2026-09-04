package com.yeowool.admin.itemtool;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * {@code /아이템 복사 <개수>} — clones the item in the OP's main hand
 * (including any custom name/lore/bound/timed metadata) and adds that many
 * total units to their inventory, split across multiple stacks if the count
 * exceeds the item's max stack size.
 */
public final class ItemCopyCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;

    public ItemCopyCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 2 || !args[0].equals("복사")) {
            messages.send(player, "itemtool.copy-usage");
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            messages.send(player, "itemtool.need-item-in-hand");
            return true;
        }

        int count;
        try {
            count = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "general.invalid-count");
            return true;
        }
        if (count <= 0) {
            messages.send(player, "itemtool.copy-count-positive");
            return true;
        }

        int maxStack = hand.getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStack);
            ItemStack copy = hand.clone();
            copy.setAmount(stackAmount);
            var leftover = player.getInventory().addItem(copy);
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            remaining -= stackAmount;
        }

        messages.send(player, "itemtool.copy-success", Placeholder.unparsed("count", String.valueOf(count)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("복사"), args[0]);
        }
        if (args.length == 2 && args[0].equals("복사")) {
            return TabCompletions.filter(List.of("1", "8", "16", "32", "64"), args[1]);
        }
        return List.of();
    }
}
