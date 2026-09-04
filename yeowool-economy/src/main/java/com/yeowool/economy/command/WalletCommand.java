package com.yeowool.economy.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.economy.PlayerResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /돈} — show balance, or {@code /돈 보내기 <닉네임> <금액>} to transfer
 * "온" between players. No transfer fee (only YeowoolMarket's player-shop
 * trades take the 3% fee per the plugin plan).
 */
public final class WalletCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final PlayerResolver resolver;

    public WalletCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.resolver = new PlayerResolver(plugin, core);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            long on = core.economyData().getBalance(player.getUniqueId());
            long bank = core.economyData().getBankBalance(player.getUniqueId());
            long cash = core.economyData().getCashBalance(player.getUniqueId());
            messages.send(player, "wallet.balance",
                    Placeholder.unparsed("on", String.format("%,d", on)),
                    Placeholder.unparsed("bank", String.format("%,d", bank)),
                    Placeholder.unparsed("cash", String.format("%,d", cash)));
            return true;
        }

        if (args[0].equals("보내기")) {
            handleSend(player, args);
            return true;
        }

        messages.send(player, "wallet.send-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("보내기"), args[0]);
        }
        if (args.length == 2 && args[0].equals("보내기")) {
            return TabCompletions.filteredOnlinePlayerNames(args[1]);
        }
        return List.of();
    }

    private void handleSend(Player sender, String[] args) {
        if (args.length != 3) {
            messages.send(sender, "wallet.send-usage");
            return;
        }

        String targetName = args[1];
        if (targetName.equalsIgnoreCase(sender.getName())) {
            messages.send(sender, "wallet.send-self");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "wallet.send-invalid-amount");
            return;
        }
        if (amount <= 0) {
            messages.send(sender, "wallet.send-invalid-amount");
            return;
        }

        if (!core.economyData().hasBalance(sender.getUniqueId(), amount)) {
            messages.send(sender, "wallet.send-insufficient");
            return;
        }

        resolver.resolve(targetName).whenComplete((targetUuid, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || targetUuid == null) {
                messages.send(sender, "wallet.send-player-not-found");
                return;
            }

            // Re-check after the (possibly async) lookup in case the balance changed meanwhile.
            if (!core.economyData().hasBalance(sender.getUniqueId(), amount)) {
                messages.send(sender, "wallet.send-insufficient");
                return;
            }

            boolean deducted = core.economyData().modifyBalance(sender.getUniqueId(), -amount, "YeowoolEconomy", "송금: " + targetName + " 에게");
            if (!deducted) {
                messages.send(sender, "wallet.send-insufficient");
                return;
            }
            boolean credited = core.economyData().modifyBalance(targetUuid, amount, "YeowoolEconomy", "송금: " + sender.getName() + " 로부터");
            if (!credited) {
                // Crediting the target somehow failed after the deduction already succeeded -
                // refund immediately rather than leaving the money destroyed.
                core.economyData().modifyBalance(sender.getUniqueId(), amount, "YeowoolEconomy", "송금 실패로 인한 환불: " + targetName);
                messages.send(sender, "wallet.send-player-not-found");
                return;
            }

            messages.send(sender, "wallet.send-success-sender",
                    Placeholder.unparsed("target", targetName),
                    Placeholder.unparsed("amount", String.format("%,d", amount)));

            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                messages.send(targetPlayer, "wallet.send-success-receiver",
                        Placeholder.unparsed("sender", sender.getName()),
                        Placeholder.unparsed("amount", String.format("%,d", amount)));
            } else {
                // Offline recipient: flush immediately instead of waiting for the
                // next autosave cycle, since this money only exists in cache.
                core.playerData().getIfLoaded(targetUuid).ifPresent(data -> core.playerData().save(data));
            }
        }));
    }
}
