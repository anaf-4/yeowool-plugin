package com.yeowool.market.citizens;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Only registered on the server where the shop NPC actually lives (see
 * {@code npc-shop.npc-server-id}). Finishes a cross-server {@code /상점이동}
 * that was started from another server: {@link ShopLocationCommand} marks
 * {@link ShopLocationCommand#PENDING_SETTING_KEY} on the player before
 * sending them here, and this listener checks for (and clears) that flag the
 * moment they join, teleporting them to the NPC a second later — long enough
 * for their client to have actually finished loading into the world.
 */
public final class ShopTeleportJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final ShopLocationCommand shopLocationCommand;

    public ShopTeleportJoinListener(JavaPlugin plugin, YeowoolCoreAPI core, ShopLocationCommand shopLocationCommand) {
        this.plugin = plugin;
        this.core = core;
        this.shopLocationCommand = shopLocationCommand;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        if (!"true".equals(data.getSetting(ShopLocationCommand.PENDING_SETTING_KEY, "false"))) {
            return;
        }
        data.setSetting(ShopLocationCommand.PENDING_SETTING_KEY, "false");
        Bukkit.getScheduler().runTaskLater(plugin, () -> shopLocationCommand.teleportToNpc(player), 20L);
    }
}
