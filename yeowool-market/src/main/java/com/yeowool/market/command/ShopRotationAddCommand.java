package com.yeowool.market.command;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.npcshop.ShopPricedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /상점로테이션추가 <상점ID>} — adds the item held in the admin's main
 * hand to that shop's rotation pool. Price it with {@code /상점아이템설정}
 * first, same as a regular shop item; {@code /상점로테이션설정} decides how
 * many of the pool actually show up at once and where.
 */
public final class ShopRotationAddCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final AdminShopStore store;

    public ShopRotationAddCommand(JavaPlugin plugin, AdminShopStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다. (손에 든 아이템을 로테이션 풀에 추가합니다)", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /상점로테이션추가 [상점ID] (주손에 든, 가격이 설정된 아이템 기준)", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        if (!store.isAdminShop(id)) {
            sender.sendMessage(Component.text("존재하지 않거나 config.yml로 정의된 상점입니다 (먼저 /상점수정으로 가져오세요): " + id, NamedTextColor.RED));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            sender.sendMessage(Component.text("풀에 추가할 아이템이 없습니다. 손에 아이템을 들고 사용하세요.", NamedTextColor.RED));
            return true;
        }
        if (ShopPricedItem.buyPrice(plugin, held) <= 0 && ShopPricedItem.sellPrice(plugin, held) <= 0) {
            sender.sendMessage(Component.text("가격이 설정되지 않은 아이템입니다. 먼저 /상점아이템설정으로 가격을 설정하세요.", NamedTextColor.RED));
            return true;
        }

        store.addToRotationPool(id, held.clone());
        sender.sendMessage(Component.text("상점 [" + id + "]의 로테이션 풀에 아이템을 추가했습니다. (현재 " + store.rotationPoolSize(id) + "개)", NamedTextColor.GREEN));
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
