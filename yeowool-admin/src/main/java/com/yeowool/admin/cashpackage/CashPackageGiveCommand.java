package com.yeowool.admin.cashpackage;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.OfflinePlayerResolver;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /패키지지급 <닉네임> <패키지이름>} — mails every item in the named
 * package to a player at once ({@link YeowoolCoreAPI#mailbox()} delivers
 * instantly if they're online, stores it otherwise). Works from console (no
 * player-only check), so a Tebex "Game Server Command" package product can
 * call this directly on purchase — same role {@code /캐시지급} plays for
 * currency-only packages.
 */
public final class CashPackageGiveCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final CashPackageManager packageManager;

    public CashPackageGiveCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, CashPackageManager packageManager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.packageManager = packageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            messages.send(sender, "cashpackage.give-usage");
            return true;
        }
        var pkg = packageManager.find(args[1]);
        if (pkg.isEmpty()) {
            messages.send(sender, "cashpackage.not-found", Placeholder.unparsed("name", args[1]));
            return true;
        }
        String targetName = args[0];
        OfflinePlayerResolver.resolve(plugin, targetName, target -> {
            for (ItemStack item : pkg.get().items()) {
                core.mailbox().deliverOrStore(target.getUniqueId(), item.clone(), "YeowoolAdmin",
                        "패키지 지급: " + pkg.get().name());
            }
            messages.send(sender, "cashpackage.give-success",
                    Placeholder.unparsed("target", targetName),
                    Placeholder.unparsed("name", pkg.get().name()));

            Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
            if (onlineTarget != null) {
                messages.send(onlineTarget, "cashpackage.give-received", Placeholder.unparsed("name", pkg.get().name()));
            }
        }, () -> messages.send(sender, "general.player-not-found"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        if (args.length == 2) {
            List<String> names = packageManager.all().stream().map(CashPackage::name).toList();
            return TabCompletions.filter(names, args[1]);
        }
        return List.of();
    }
}
