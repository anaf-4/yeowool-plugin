package com.yeowool.market.command;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.trade.TradeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /거래 <닉네임>} sends a request; {@code /거래 수락} accepts the most
 * recent pending one.
 */
public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final TradeManager manager;
    private final MessageService messages;

    public TradeCommand(TradeManager manager, MessageService messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "trade.usage");
            return true;
        }
        if (args[0].equals("수락")) {
            manager.accept(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "trade.player-not-found");
            return true;
        }
        manager.sendRequest(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>(TabCompletions.onlinePlayerNames());
        candidates.add("수락");
        return TabCompletions.filter(candidates, args[0]);
    }
}
