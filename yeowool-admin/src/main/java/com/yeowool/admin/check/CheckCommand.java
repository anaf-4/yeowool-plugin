package com.yeowool.admin.check;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /수표 <금액>} (온) and {@code /유료수표 <금액>} (캐시) — both bound to
 * this same class, parametrized by which {@link CurrencyType} to stamp onto
 * the created {@link CheckItem}. Gives the check straight to the admin's own
 * inventory, same as {@code /쿠폰생성}'s "held item becomes the reward"
 * convention but in reverse (here the command manufactures the item).
 */
public final class CheckCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final CurrencyType currency;
    private final String commandLabel;

    public CheckCommand(JavaPlugin plugin, MessageService messages, CurrencyType currency, String commandLabel) {
        this.plugin = plugin;
        this.messages = messages;
        this.currency = currency;
        this.commandLabel = commandLabel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "check.usage", Placeholder.unparsed("label", commandLabel));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            messages.send(sender, "check.invalid-amount");
            return true;
        }
        if (amount <= 0) {
            messages.send(sender, "check.amount-positive");
            return true;
        }

        var leftover = player.getInventory().addItem(CheckItem.create(plugin, amount, currency));
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        messages.send(sender, "check.create-success",
                Placeholder.unparsed("amount", String.format("%,d", amount)),
                Placeholder.unparsed("currency", currency.displayName()));
        return true;
    }
}
