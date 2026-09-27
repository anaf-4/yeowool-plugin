package com.yeowool.life.mount;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** {@code /탈것} (owned mounts GUI) and {@code /탈것이용권 <펫ID> [수량] [닉네임]} (staff: issue vouchers). */
public final class MountCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.life.mount.manage";
    private static final int MAX_AMOUNT = 64;

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final MountVoucherItem voucherItem;
    private final Map<String, MountDefinition> mounts;

    public MountCommand(YeowoolCoreAPI core, MessageService messages, MountVoucherItem voucherItem, Map<String, MountDefinition> mounts) {
        this.core = core;
        this.messages = messages;
        this.voucherItem = voucherItem;
        this.mounts = mounts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("탈것이용권")) {
            issue(sender, args);
        } else {
            list(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        List<MountDefinition> owned = mounts.values().stream().filter(m -> player.hasPermission(m.permission())).toList();
        if (owned.isEmpty()) {
            messages.send(player, "mount.none-owned");
            return;
        }
        new MountListGui(owned).open(player);
    }

    private void issue(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 1) {
            messages.send(sender, "mount.issue-usage");
            return;
        }
        MountDefinition mount = mounts.get(args[0]);
        if (mount == null) {
            messages.send(sender, "mount.unknown", Placeholder.unparsed("id", args[0]));
            return;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                messages.send(sender, "mount.invalid-amount", Placeholder.unparsed("max", String.valueOf(MAX_AMOUNT)));
                return;
            }
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else {
            target = sender instanceof Player self ? self : null;
        }
        if (target == null) {
            messages.send(sender, "mount.target-offline");
            return;
        }
        for (int i = 0; i < amount; i++) {
            core.mailbox().deliverOrStore(target.getUniqueId(), voucherItem.create(mount), "YeowoolLife", "탈것 이용권");
        }
        messages.send(sender, "mount.issued",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("mount", mount.displayName()),
                Placeholder.unparsed("amount", String.valueOf(amount)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equals("탈것이용권") || !sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return mounts.keySet().stream().filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 3) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
