package com.yeowool.life.bag;

import com.yeowool.core.api.service.MessageService;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** {@code /가방확장권 지급 <개수>} (OP-only, {@code yeowool.admin}) — gives the caller that many {@link TicketChestListener#TICKET_ID} items. */
public final class BagExpandTicketCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;

    public BagExpandTicketCommand(MessageService messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 2 || !args[0].equals("지급")) {
            messages.send(player, "bag.ticket-usage");
            return true;
        }
        if (!player.hasPermission("yeowool.admin")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "bag.ticket-usage");
            return true;
        }
        if (amount <= 0) {
            messages.send(player, "bag.ticket-usage");
            return true;
        }

        ItemStack give = resolveTicket(amount);
        var leftover = player.getInventory().addItem(give);
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));

        messages.send(player, "bag.ticket-grant-success", Placeholder.unparsed("amount", String.valueOf(amount)));
        return true;
    }

    private ItemStack resolveTicket(int amount) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(TicketChestListener.TICKET_ID);
            if (custom != null) {
                ItemStack stack = custom.getItemStack();
                stack.setAmount(amount);
                return stack;
            }
        }
        return new ItemStack(Material.PAPER, amount);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return "지급".startsWith(args[0]) ? List.of("지급") : List.of();
        }
        return List.of();
    }
}
