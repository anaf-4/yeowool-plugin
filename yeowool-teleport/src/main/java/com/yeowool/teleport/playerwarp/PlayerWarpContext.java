package com.yeowool.teleport.playerwarp;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.teleport.TeleportService;
import org.bukkit.plugin.java.JavaPlugin;

/** Bundles everything every {@code playerwarp} GUI/listener needs, so constructors don't take eight separate parameters. */
public record PlayerWarpContext(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                 PlayerWarpManager warps, PlayerWarpRatingManager ratings, PlayerWarpFavoriteManager favorites,
                                 TeleportService teleportService, PlayerWarpConfig config) {
}
