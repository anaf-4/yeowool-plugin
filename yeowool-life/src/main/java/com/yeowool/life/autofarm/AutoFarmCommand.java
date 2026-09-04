package com.yeowool.life.autofarm;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /자동줍기권}/{@code /자동심기권} (same class, parameterized by
 * {@link AutoFarmType} — mirrors {@code BannedItemCommand}'s pattern):
 * {@code 지급 <횟수>} (OP-only, {@code yeowool.admin}) gives the caller a
 * voucher item worth that many charges — {@code -1} means unlimited.
 */
public final class AutoFarmCommand implements CommandExecutor, TabCompleter {

    private final AutoFarmType type;
    private final AutoFarmVoucherItem voucherItem;
    private final MessageService messages;

    public AutoFarmCommand(AutoFarmType type, AutoFarmVoucherItem voucherItem, MessageService messages) {
        this.type = type;
        this.voucherItem = voucherItem;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 2 && args[0].equals("지급")) {
            grant(player, args[1]);
            return true;
        }
        messages.send(player, "autofarm.usage", Placeholder.unparsed("voucher", type.voucherName()));
        return true;
    }

    private void grant(Player player, String rawCount) {
        if (!player.hasPermission("yeowool.admin")) {
            messages.send(player, "general.no-permission");
            return;
        }
        long count;
        try {
            count = Long.parseLong(rawCount);
        } catch (NumberFormatException e) {
            messages.send(player, "autofarm.grant-usage", Placeholder.unparsed("voucher", type.voucherName()));
            return;
        }
        long charges = count < 0 ? AutoFarmType.INFINITE : count;
        var give = voucherItem.create(type, charges);
        var leftover = player.getInventory().addItem(give);
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));

        messages.send(player, count < 0 ? "autofarm.grant-success-infinite" : "autofarm.grant-success",
                Placeholder.unparsed("type", type.label()),
                Placeholder.unparsed("amount", String.format("%,d", count)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("지급"), args[0]);
        }
        return List.of();
    }
}
