package com.yeowool.market.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.market.npcshop.NPCShopGui;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopMainMenuGui;
import com.yeowool.market.npcshop.ShopRotationManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * {@code /상점 [상점ID]} — with an id, deep-links straight into that shop's
 * {@link NPCShopGui} (page 0), same as clicking its category button would.
 * With no argument, opens whatever {@code npc-shop.default-open} in
 * config.yml names: {@code "menu"} (default) for {@link ShopMainMenuGui},
 * or a specific shop id to skip the category picker entirely and always
 * jump straight into that one shop.
 */
public final class NPCShopCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Map<String, ShopDefinition> shops;
    private final ShopRotationManager rotationManager;

    public NPCShopCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, Map<String, ShopDefinition> shops,
                           ShopRotationManager rotationManager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.shops = shops;
        this.rotationManager = rotationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (shops.isEmpty()) {
            messages.send(player, "npcshop.no-shops-configured");
            return true;
        }

        if (args.length == 0) {
            String defaultOpen = plugin.getConfig().getString("npc-shop.default-open", "menu");
            ShopDefinition defaultShop = defaultOpen == null ? null : shops.get(defaultOpen);
            if (defaultShop != null) {
                new NPCShopGui(plugin, core, messages, shops, defaultShop, rotationManager, 0).open(player);
            } else {
                new ShopMainMenuGui(plugin, core, messages, shops, rotationManager).open(player);
            }
            return true;
        }

        ShopDefinition shop = shops.get(args[0]);
        if (shop == null) {
            messages.send(player, "npcshop.shop-not-found", Placeholder.unparsed("id", args[0]));
            return true;
        }
        new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
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
