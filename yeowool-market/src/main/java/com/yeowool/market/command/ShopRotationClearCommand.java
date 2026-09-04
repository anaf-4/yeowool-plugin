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

/** {@code /상점로테이션제거 <상점ID>} — clears the shop's rotation slots/interval and its whole pool. */
public final class ShopRotationClearCommand implements CommandExecutor, TabCompleter {

    private final AdminShopStore store;

    public ShopRotationClearCommand(AdminShopStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /상점로테이션제거 [상점ID]", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        var result = store.clearRotation(id);
        if (result == AdminShopStore.RotationResult.NOT_FOUND) {
            sender.sendMessage(Component.text("존재하지 않거나 config.yml로 정의된 상점입니다: " + id, NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("상점 [" + id + "]의 로테이션을 제거했습니다.", NamedTextColor.GREEN));
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
