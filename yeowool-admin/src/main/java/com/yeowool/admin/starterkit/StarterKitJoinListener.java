package com.yeowool.admin.starterkit;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Section 5 (첫 접속 시스템) of the plugin plan, moved here from YeowoolLand
 * so the kit contents can be managed through {@link StarterKitEditorGui}
 * ({@code /여울관리 기본템}) instead of a static config list.
 */
public final class StarterKitJoinListener implements Listener {

    private static final String SETTING_KEY = "starter.kit_given";

    private final YeowoolCoreAPI core;
    private final StarterKitService service;

    public StarterKitJoinListener(YeowoolCoreAPI core, StarterKitService service) {
        this.core = core;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var data = core.playerData().getOnline(player.getUniqueId());

        if (data.getSetting(SETTING_KEY, "false").equals("true")) {
            return;
        }
        data.setSetting(SETTING_KEY, "true");
        service.give(player);
    }
}
