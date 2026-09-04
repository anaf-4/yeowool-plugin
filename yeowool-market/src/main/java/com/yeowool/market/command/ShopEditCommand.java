package com.yeowool.market.command;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.adminshop.AdminShopEditorGui;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.npcshop.ShopDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * {@code /상점수정 <상점ID>} — opens {@link AdminShopEditorGui} at page 1. Works
 * on config.yml shops too: the first time one is edited this way, {@link
 * AdminShopStore#adopt} snapshots it into admin-managed storage (config.yml
 * itself is left untouched) so it can be saved back through the same editor.
 */
public final class ShopEditCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final AdminShopStore store;
    private final Map<String, ShopDefinition> shops;
    private final MessageService messages;

    public ShopEditCommand(JavaPlugin plugin, AdminShopStore store, Map<String, ShopDefinition> shops, MessageService messages) {
        this.plugin = plugin;
        this.store = store;
        this.shops = shops;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("사용법: /상점수정 [상점ID]", NamedTextColor.RED));
            return true;
        }
        String id = args[0];
        if (!store.isAdminShop(id)) {
            ShopDefinition existing = shops.get(id);
            if (existing == null) {
                sender.sendMessage(Component.text("존재하지 않는 상점입니다: " + id, NamedTextColor.RED));
                return true;
            }
            store.adopt(id, existing);
        }
        new AdminShopEditorGui(plugin, store, id, 0).open(player);
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
