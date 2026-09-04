package com.yeowool.market.citizens;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.npcshop.NPCShopGui;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopRotationManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Optional Citizens integration (only registered when Citizens is installed
 * — see {@link com.yeowool.market.YeowoolMarket}): right-clicking an NPC
 * listed in {@code npc-shop.citizens-npc-shops} opens the mapped shop, same
 * as {@code /상점 <id>}, so a server can place a visible shopkeeper per shop
 * instead of relying only on the command.
 */
public final class CitizensShopListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Map<Integer, String> npcIdToShopId;
    private final Map<String, ShopDefinition> shops;
    private final ShopRotationManager rotationManager;

    public CitizensShopListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, Map<Integer, String> npcIdToShopId,
                                 Map<String, ShopDefinition> shops, ShopRotationManager rotationManager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.npcIdToShopId = npcIdToShopId;
        this.shops = shops;
        this.rotationManager = rotationManager;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        String shopId = npcIdToShopId.get(event.getNPC().getId());
        if (shopId == null) {
            return;
        }
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) {
            return;
        }
        Player player = event.getClicker();
        new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
    }
}
