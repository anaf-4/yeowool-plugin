package com.yeowool.community.nickname;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /한글닉네임설정권 [플레이어]} — grants one voucher item to the
 * command sender, or to the named player when given (online or offline —
 * an offline target gets it delivered to their mailbox, same as
 * {@code /여울관리 우편}, since there's no inventory to hand it to
 * directly). OP-only (see {@code plugin.yml}'s {@code default: op} on this
 * command).
 */
public final class NicknameVoucherCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final String customItemId;
    private final MessageService messages;

    public NicknameVoucherCommand(JavaPlugin plugin, YeowoolCoreAPI core, String customItemId, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.customItemId = customItemId;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            Player onlineTarget = Bukkit.getPlayerExact(args[0]);
            if (onlineTarget != null) {
                giveDirectly(onlineTarget);
                messages.send(onlineTarget, "nickname.voucher-received");
                if (sender != onlineTarget) {
                    messages.send(sender, "nickname.voucher-given", Placeholder.unparsed("player", onlineTarget.getName()));
                }
                return true;
            }

            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (offline.getUniqueId() == null || !offline.hasPlayedBefore()) {
                messages.send(sender, "nickname.player-not-found");
                return true;
            }
            core.mailbox().deliverOrStore(offline.getUniqueId(), NicknameVoucherItem.create(plugin, customItemId),
                    "YeowoolCommunity", "한글 닉네임 설정권");
            messages.send(sender, "nickname.voucher-given-mailbox", Placeholder.unparsed("player", args[0]));
            return true;
        }

        if (!(sender instanceof Player self)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        giveDirectly(self);
        messages.send(self, "nickname.voucher-received");
        return true;
    }

    private void giveDirectly(Player target) {
        var leftover = target.getInventory().addItem(NicknameVoucherItem.create(plugin, customItemId));
        leftover.values().forEach(extra -> target.getWorld().dropItemNaturally(target.getLocation(), extra));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return com.yeowool.core.util.TabCompletions.filter(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
        }
        return List.of();
    }
}
