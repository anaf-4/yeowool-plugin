package com.yeowool.market.command;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** {@code /상점페이지추가 <상점ID>} — appends a new (empty) page to an admin-created shop. */
public final class ShopPageAddCommand implements CommandExecutor, TabCompleter {

    private final AdminShopStore store;

    public ShopPageAddCommand(AdminShopStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /상점페이지추가 [상점ID]", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        var newCount = store.addPage(id);
        if (newCount.isEmpty()) {
            sender.sendMessage(Component.text("존재하지 않거나 config.yml로 정의된 상점입니다: " + id, NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("상점 [" + id + "]에 페이지를 추가했습니다. (현재 " + newCount.get() + "페이지)", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.copyOf(store.ids()), args[0]);
        }
        return List.of();
    }
}
