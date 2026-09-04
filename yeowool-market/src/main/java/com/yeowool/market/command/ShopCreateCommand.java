package com.yeowool.market.command;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopLayout;
import com.yeowool.market.npcshop.ShopMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@code /상점생성 <구매모드|판매모드|모두> <상점ID> <GUI 내부 상점 이름...>} — creates
 * an empty (1-page, no items) admin-managed NPC shop, immediately visible
 * via {@code /상점 <상점ID>}. Item content is added afterward through
 * {@code /상점아이템설정} (price) + {@code /상점수정} (placement). Refuses once
 * {@link ShopLayout#MAIN_MENU_SLOTS} is full, since the main menu (both
 * config.yml shops and admin-created ones share it) has no more room for
 * another category button.
 */
public final class ShopCreateCommand implements CommandExecutor, TabCompleter {

    private final AdminShopStore store;
    private final Map<String, ShopDefinition> shops;

    public ShopCreateCommand(AdminShopStore store, Map<String, ShopDefinition> shops) {
        this.store = store;
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("사용법: /상점생성 [구매모드|판매모드|모두] [상점ID] [GUI 내부 상점 이름]", NamedTextColor.RED));
            return true;
        }

        if (shops.size() >= ShopLayout.MAIN_MENU_SLOTS.size()) {
            sender.sendMessage(Component.text("상점 갯수가 " + ShopLayout.MAIN_MENU_SLOTS.size() + "개 이상이면 더이상 상점을 추가할 수 없습니다.", NamedTextColor.RED));
            return true;
        }

        ShopMode mode = parseMode(args[0]);
        if (mode == null) {
            sender.sendMessage(Component.text("모드는 구매모드/판매모드/모두 중 하나여야 합니다.", NamedTextColor.RED));
            return true;
        }

        String id = args[1];
        if (!id.matches("[a-zA-Z0-9_-]{1,32}")) {
            sender.sendMessage(Component.text("상점ID는 영문/숫자/-/_ 로만 이루어진 32자 이하여야 합니다.", NamedTextColor.RED));
            return true;
        }

        String title = String.join(" ", Arrays.asList(args).subList(2, args.length));

        var result = store.create(id, mode, title);
        if (result == AdminShopStore.CreateResult.ALREADY_EXISTS) {
            sender.sendMessage(Component.text("이미 존재하는 상점ID입니다: " + id, NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("상점 [" + id + "]을(를) 생성했습니다. (" + modeLabel(mode) + ", 이름: " + title + ")", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("아이템 추가: 아이템을 들고 /상점아이템설정 " + id + " [구매가] [판매가] [온|캐시] 후 /상점수정 " + id + "에서 배치하세요.", NamedTextColor.GRAY));
        return true;
    }

    private ShopMode parseMode(String arg) {
        return switch (arg) {
            case "구매모드" -> ShopMode.BUY_ONLY;
            case "판매모드" -> ShopMode.SELL_ONLY;
            case "모두" -> ShopMode.BOTH;
            default -> null;
        };
    }

    private String modeLabel(ShopMode mode) {
        return switch (mode) {
            case BUY_ONLY -> "구매모드";
            case SELL_ONLY -> "판매모드";
            case BOTH -> "모두";
        };
    }

    public static List<String> modeKeywords() {
        return List.of("구매모드", "판매모드", "모두");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(modeKeywords(), args[0]);
        }
        return List.of();
    }
}
