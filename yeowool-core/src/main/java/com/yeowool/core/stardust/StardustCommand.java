package com.yeowool.core.stardust;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.StardustService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** {@code /별조각} — own balance; staff (and console, for other plugins' reward commands) 확인/지급/회수. */
public final class StardustCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.core.admin";

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final StardustService stardust;

    public StardustCommand(JavaPlugin plugin, MessageService messages, StardustService stardust) {
        this.plugin = plugin;
        this.messages = messages;
        this.stardust = stardust;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "stardust.usage");
                return true;
            }
            stardust.balance(player.getUniqueId()).whenComplete((balance, error) -> reply(sender, error != null ? "stardust.error" : "stardust.balance",
                    Placeholder.unparsed("amount", String.format("%,d", balance == null ? 0 : balance))));
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "stardust.usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            messages.send(sender, "stardust.unknown-player");
            return true;
        }
        String name = target.getName() == null ? args[1] : target.getName();
        TagResolver playerTag = Placeholder.unparsed("player", name);
        switch (args[0]) {
            case "확인" -> stardust.balance(target.getUniqueId()).whenComplete((balance, error) ->
                    reply(sender, error != null ? "stardust.error" : "stardust.balance-other", playerTag,
                            Placeholder.unparsed("amount", String.format("%,d", balance == null ? 0 : balance))));
            case "지급", "회수" -> {
                long amount;
                try {
                    amount = args.length >= 3 ? Long.parseLong(args[2]) : -1;
                } catch (NumberFormatException e) {
                    amount = -1;
                }
                if (amount <= 0) {
                    messages.send(sender, "stardust.usage");
                    return true;
                }
                TagResolver amountTag = Placeholder.unparsed("amount", String.format("%,d", amount));
                String reason = "관리진 " + args[0] + " (" + sender.getName() + ")";
                if (args[0].equals("지급")) {
                    stardust.grant(target.getUniqueId(), amount, "YeowoolCore", reason).whenComplete((granted, error) ->
                            reply(sender, error != null ? "stardust.error" : "stardust.granted", playerTag, amountTag));
                } else {
                    stardust.spend(target.getUniqueId(), amount, "YeowoolCore", reason).whenComplete((taken, error) ->
                            reply(sender, error != null ? "stardust.error" : Boolean.TRUE.equals(taken) ? "stardust.taken" : "stardust.insufficient",
                                    playerTag, amountTag));
                }
            }
            default -> messages.send(sender, "stardust.usage");
        }
        return true;
    }

    private void reply(CommandSender sender, String key, TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("확인", "지급", "회수").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
