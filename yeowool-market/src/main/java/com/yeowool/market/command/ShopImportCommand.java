package com.yeowool.market.command;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.npcshop.ShopDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Map;

/**
 * {@code /상점가져오기 <원본상점ID> <대상상점ID>} — bulk-registers every item from
 * one shop into another instead of placing them one at a time through
 * {@code /상점수정}. Typical use: hand-write a big item list under a scratch
 * shop id in config.yml, then import it into a real admin-managed shop in
 * one command. Source can be any shop (config.yml or admin); target is
 * adopted automatically if it isn't already admin-managed.
 */
public final class ShopImportCommand implements CommandExecutor, TabCompleter {

    private final AdminShopStore store;
    private final Map<String, ShopDefinition> shops;

    public ShopImportCommand(AdminShopStore store, Map<String, ShopDefinition> shops) {
        this.store = store;
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("사용법: /상점가져오기 [원본상점ID] [대상상점ID]", NamedTextColor.RED));
            return true;
        }
        String sourceId = args[0];
        String targetId = args[1];
        ShopDefinition source = shops.get(sourceId);
        if (source == null) {
            sender.sendMessage(Component.text("존재하지 않는 원본 상점입니다: " + sourceId, NamedTextColor.RED));
            return true;
        }
        if (shops.get(targetId) == null) {
            sender.sendMessage(Component.text("존재하지 않는 대상 상점입니다: " + targetId, NamedTextColor.RED));
            return true;
        }

        var result = store.importItems(targetId, source);
        if (result == AdminShopStore.ImportResult.SOURCE_NOT_FOUND || result == AdminShopStore.ImportResult.TARGET_NOT_FOUND) {
            sender.sendMessage(Component.text("가져오기에 실패했습니다.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("상점 [" + sourceId + "]의 아이템을 [" + targetId + "]로 가져왔습니다. ("
                + shops.get(targetId).items().size() + "개 아이템)", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 || args.length == 2) {
            return TabCompletions.filter(List.copyOf(shops.keySet()), args[args.length - 1]);
        }
        return List.of();
    }
}
