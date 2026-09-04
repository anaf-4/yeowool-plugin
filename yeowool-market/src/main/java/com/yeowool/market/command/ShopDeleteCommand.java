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
 * {@code /상점제거 <상점ID>} — works on any registered shop, including
 * config.yml ones that were never adopted via {@code /상점수정}. For those,
 * there's no database row to delete, so it only removes the shop from this
 * server run's live list; {@code config.yml} itself is never rewritten, so
 * the shop comes back on the next restart unless removed from the file too.
 */
public final class ShopDeleteCommand implements CommandExecutor, TabCompleter {

    private final AdminShopStore store;
    private final Map<String, ShopDefinition> shops;

    public ShopDeleteCommand(AdminShopStore store, Map<String, ShopDefinition> shops) {
        this.store = store;
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /상점제거 [상점ID]", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        switch (store.delete(id)) {
            case NOT_FOUND -> sender.sendMessage(Component.text("존재하지 않는 상점입니다: " + id, NamedTextColor.RED));
            case SUCCESS -> sender.sendMessage(Component.text("상점 [" + id + "]을(를) 삭제했습니다.", NamedTextColor.GREEN));
            case SUCCESS_SESSION_ONLY -> {
                sender.sendMessage(Component.text("상점 [" + id + "]을(를) 삭제했습니다.", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("이 상점은 config.yml에 정의되어 있어서 이번 서버 실행 동안만 사라집니다 — "
                        + "재시작 시 다시 나타나지 않게 하려면 config.yml에서도 직접 지워주세요.", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.copyOf(shops.keySet()), args[0]);
        }
        return List.of();
    }
}
