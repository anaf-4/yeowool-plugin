package com.yeowool.market.command;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopPriceGui;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.adminshop.ShopPriceAnvilListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /상점아이템가격 <상점ID>} — opens {@link AdminShopPriceGui} at page 1, the
 * read-only companion to {@code /상점아이템설정}'s placement editor: right-click
 * an already-placed item there to price it via {@link ShopPriceAnvilListener}'s
 * anvil wizard instead of typing prices on the command line.
 */
public final class ShopPriceCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final AdminShopStore store;
    private final ShopPriceAnvilListener priceListener;
    private final MessageService messages;

    public ShopPriceCommand(JavaPlugin plugin, AdminShopStore store, ShopPriceAnvilListener priceListener, MessageService messages) {
        this.plugin = plugin;
        this.store = store;
        this.priceListener = priceListener;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /상점아이템가격 [상점ID]", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        if (!store.isAdminShop(id)) {
            sender.sendMessage(Component.text("존재하지 않는 상점입니다: " + id, NamedTextColor.RED));
            return true;
        }
        new AdminShopPriceGui(plugin, store, priceListener, id, 0).open(player);
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
