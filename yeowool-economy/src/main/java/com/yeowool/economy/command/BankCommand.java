package com.yeowool.economy.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /은행}, {@code /은행 입금 <금액>}, {@code /은행 출금 <금액>}.
 * Moves "온" between the wallet and bank balances stored on the core player
 * row; no interest/limits yet (see plugin plan section 3.1 for future scope).
 */
public final class BankCommand implements CommandExecutor, TabCompleter {

    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public BankCommand(YeowoolCoreAPI core, MessageService messages) {
        this.core = core;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            long bank = core.economyData().getBankBalance(player.getUniqueId());
            messages.send(player, "bank.balance", Placeholder.unparsed("bank", String.format("%,d", bank)));
            return true;
        }

        if (args.length != 2 || (!args[0].equals("입금") && !args[0].equals("출금"))) {
            messages.send(player, "bank.usage");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "bank.invalid-amount");
            return true;
        }
        if (amount <= 0) {
            messages.send(player, "bank.invalid-amount");
            return true;
        }

        var uuid = player.getUniqueId();
        if (args[0].equals("입금")) {
            if (!core.economyData().hasBalance(uuid, amount)) {
                messages.send(player, "bank.insufficient-wallet");
                return true;
            }
            core.economyData().modifyBalance(uuid, -amount, "YeowoolEconomy", "은행 입금");
            core.economyData().modifyBankBalance(uuid, amount, "YeowoolEconomy", "은행 입금");
            messages.send(player, "bank.deposit-success", Placeholder.unparsed("amount", String.format("%,d", amount)));
        } else {
            if (!core.economyData().hasBankBalance(uuid, amount)) {
                messages.send(player, "bank.insufficient-bank");
                return true;
            }
            core.economyData().modifyBankBalance(uuid, -amount, "YeowoolEconomy", "은행 출금");
            core.economyData().modifyBalance(uuid, amount, "YeowoolEconomy", "은행 출금");
            messages.send(player, "bank.withdraw-success", Placeholder.unparsed("amount", String.format("%,d", amount)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("입금", "출금"), args[0]);
        }
        return List.of();
    }
}
