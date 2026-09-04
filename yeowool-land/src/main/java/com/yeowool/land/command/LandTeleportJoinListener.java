package com.yeowool.land.command;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Only registered on the server where land actually lives (see
 * {@code land-teleport.land-server-id}). Finishes a cross-server
 * {@code /토지이동} that was started from another server: {@link
 * LandTeleportCommand} marks {@link LandTeleportCommand#PENDING_SETTING_KEY}
 * on the player before sending them here, and this listener checks for (and
 * clears) that flag the moment they join, teleporting them to their land a
 * second later — long enough for their client to have actually finished
 * loading into the world.
 */
public final class LandTeleportJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final LandTeleportCommand landTeleportCommand;

    public LandTeleportJoinListener(JavaPlugin plugin, YeowoolCoreAPI core, LandTeleportCommand landTeleportCommand) {
        this.plugin = plugin;
        this.core = core;
        this.landTeleportCommand = landTeleportCommand;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        if (!"true".equals(data.getSetting(LandTeleportCommand.PENDING_SETTING_KEY, "false"))) {
            return;
        }
        data.setSetting(LandTeleportCommand.PENDING_SETTING_KEY, "false");
        Bukkit.getScheduler().runTaskLater(plugin, () -> landTeleportCommand.teleportToLand(player), 20L);
    }
}
