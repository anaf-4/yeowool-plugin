package com.yeowool.market.command;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.npcshop.ShopLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /상점로테이션설정 <상점ID> <간격분> <슬롯1,슬롯2,...>} — sets which slots
 * rotate a random subset of the shop's rotation pool (added separately via
 * {@code /상점로테이션추가}) and how often, then rolls immediately. Only
 * works on admin-managed shops (adopt a config.yml one via {@code /상점수정}
 * first if needed) since YAML shops already have their own {@code rotation:}
 * config section.
 */
public final class ShopRotationSetCommand implements CommandExecutor, TabCompleter {

    private final AdminShopStore store;

    public ShopRotationSetCommand(AdminShopStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text("사용법: /상점로테이션설정 [상점ID] [간격분] [슬롯1,슬롯2,...]", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        if (!store.isAdminShop(id)) {
            sender.sendMessage(Component.text("존재하지 않거나 config.yml로 정의된 상점입니다 (먼저 /상점수정으로 가져오세요): " + id, NamedTextColor.RED));
            return true;
        }

        int intervalMinutes;
        try {
            intervalMinutes = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("간격분은 1 이상의 정수여야 합니다.", NamedTextColor.RED));
            return true;
        }
        if (intervalMinutes <= 0) {
            sender.sendMessage(Component.text("간격분은 1 이상의 정수여야 합니다.", NamedTextColor.RED));
            return true;
        }

        List<Integer> slots = new ArrayList<>();
        for (String part : args[2].split(",")) {
            try {
                int slot = Integer.parseInt(part.trim());
                if (!ShopLayout.USABLE_SLOTS.contains(slot)) {
                    sender.sendMessage(Component.text("슬롯 " + slot + "은(는) 아이템을 놓을 수 없는 자리입니다.", NamedTextColor.RED));
                    return true;
                }
                slots.add(slot);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("슬롯 목록은 쉼표로 구분된 숫자여야 합니다 (예: 10,11,12).", NamedTextColor.RED));
                return true;
            }
        }
        if (slots.isEmpty()) {
            sender.sendMessage(Component.text("슬롯을 하나 이상 지정해야 합니다.", NamedTextColor.RED));
            return true;
        }

        store.setRotation(id, intervalMinutes, slots);
        sender.sendMessage(Component.text("상점 [" + id + "]의 로테이션을 설정했습니다. (" + intervalMinutes + "분마다, 슬롯: " + slots
                + ", 현재 풀 아이템 " + store.rotationPoolSize(id) + "개) /상점로테이션추가로 풀에 아이템을 추가하세요.", NamedTextColor.GREEN));
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
