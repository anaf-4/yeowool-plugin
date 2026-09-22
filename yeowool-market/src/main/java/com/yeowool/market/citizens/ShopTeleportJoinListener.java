package com.yeowool.market.citizens;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Only registered on the server where the shop district/NPCs actually live
 * (see {@code npc-shop.npc-server-id}). Finishes whichever cross-server shop
 * teleport was started from another server — {@code /상점이동}
 * ({@link ShopLocationCommand}, a plain boolean flag since it always goes to
 * the same fixed spot) or a per-shop NPC jump from the main menu
 * ({@link ShopNpcTeleporter}, which stores the target npc id since it varies
 * per shop) — the moment the player joins, a second later so their client has
 * actually finished loading into the world.
 */
public final class ShopTeleportJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final ShopLocationCommand shopLocationCommand;

    public ShopTeleportJoinListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                     ShopLocationCommand shopLocationCommand) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.shopLocationCommand = shopLocationCommand;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = core.playerData().getOnline(player.getUniqueId());

        if ("true".equals(data.getSetting(ShopLocationCommand.PENDING_SETTING_KEY, "false"))) {
            data.setSetting(ShopLocationCommand.PENDING_SETTING_KEY, "false");
            Bukkit.getScheduler().runTaskLater(plugin, () -> shopLocationCommand.teleportToDistrict(player), 20L);
        }

        String pendingNpcId = data.getSetting(ShopNpcTeleporter.PENDING_NPC_SETTING_KEY, "");
        if (!pendingNpcId.isBlank()) {
            data.setSetting(ShopNpcTeleporter.PENDING_NPC_SETTING_KEY, "");
            int npcId = Integer.parseInt(pendingNpcId);
            Bukkit.getScheduler().runTaskLater(plugin, () -> ShopNpcTeleporter.teleportNow(player, npcId, messages), 20L);
        }
    }
}
